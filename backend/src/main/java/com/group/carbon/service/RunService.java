package com.group.carbon.service;

import com.group.carbon.common.BizException;
import com.group.carbon.common.EnergyTypes;
import com.group.carbon.common.Types.*;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 核算运行编排：
 * 1. 启动时快照当前已批准因子清单（run_factor_bindings），运行期间因子被替换不影响本运行；
 * 2. 按 工厂地域 + 能源类型 + 适用期间 匹配批准因子，缺因子只把对应指标列为待核，不用集团平均数兜底；
 * 3. 结果与来源行、因子版本建立血缘。
 */
@Service
public class RunService {
  private final CalcRunRepo runRepo;
  private final RunFactorBindingRepo bindingRepo;
  private final EmissionResultRepo resultRepo;
  private final EmissionLineageRepo lineageRepo;
  private final SourceRowRepo rowRepo;
  private final SourceBatchRepo batchRepo;
  private final FactorRepo factorRepo;
  private final FactorVersionRepo versionRepo;
  private final PlantRepo plantRepo;
  private final ConversionService conversionService;

  public RunService(CalcRunRepo runRepo, RunFactorBindingRepo bindingRepo,
                    EmissionResultRepo resultRepo, EmissionLineageRepo lineageRepo,
                    SourceRowRepo rowRepo, SourceBatchRepo batchRepo,
                    FactorRepo factorRepo, FactorVersionRepo versionRepo, PlantRepo plantRepo,
                    @Lazy ConversionService conversionService) {
    this.runRepo = runRepo;
    this.bindingRepo = bindingRepo;
    this.resultRepo = resultRepo;
    this.lineageRepo = lineageRepo;
    this.rowRepo = rowRepo;
    this.batchRepo = batchRepo;
    this.factorRepo = factorRepo;
    this.versionRepo = versionRepo;
    this.plantRepo = plantRepo;
    this.conversionService = conversionService;
  }

  public static void validatePeriod(String period) {
    if (period == null || !period.matches("\\d{4}-(0[1-9]|1[0-2])")) {
      throw BizException.badRequest("期间格式必须为 YYYY-MM，例如 2025-06");
    }
  }

  /** 创建正式运行：绑定启动时刻的批准因子清单。并发重复运行由数据库唯一索引拦截。 */
  @Transactional
  public CalcRun createOfficialRun(String period, String username) {
    validatePeriod(period);
    CalcRun run = new CalcRun();
    run.setRunNo(newNo("RUN"));
    run.setPeriod(period);
    run.setPurpose(RunPurpose.OFFICIAL);
    run.setStatus(RunStatus.RUNNING);
    run.setTriggeredBy(username);
    run.setStartedAt(LocalDateTime.now());
    runRepo.save(run);
    snapshotBindings(run);
    return run;
  }

  /** 候选运行（影响评估用）：同步创建并立即执行完成。 */
  @Transactional
  public CalcRun createCandidateRun(String period, String triggeredBy) {
    validatePeriod(period);
    CalcRun run = new CalcRun();
    run.setRunNo(newNo("CAND"));
    run.setPeriod(period);
    run.setPurpose(RunPurpose.CANDIDATE);
    run.setStatus(RunStatus.RUNNING);
    run.setTriggeredBy(triggeredBy);
    run.setStartedAt(LocalDateTime.now());
    runRepo.save(run);
    snapshotBindings(run);
    execute(run.getId());
    return runRepo.findById(run.getId()).orElseThrow();
  }

  private void snapshotBindings(CalcRun run) {
    LocalDate periodStart = LocalDate.parse(run.getPeriod() + "-01");
    List<FactorVersion> approved = versionRepo.findByStatus(FactorStatus.APPROVED);
    // 同一因子若存在多个覆盖该期间的批准版本，取版本号最大者
    Map<Long, FactorVersion> byFactor = new LinkedHashMap<>();
    for (FactorVersion v : approved) {
      boolean covers = !v.getValidFrom().isAfter(periodStart)
          && (v.getValidTo() == null || !v.getValidTo().isBefore(periodStart));
      if (!covers) continue;
      FactorVersion prev = byFactor.get(v.getFactorId());
      if (prev == null || v.getVersionNo() > prev.getVersionNo()) byFactor.put(v.getFactorId(), v);
    }
    for (FactorVersion v : byFactor.values()) {
      Factor f = factorRepo.findById(v.getFactorId()).orElseThrow();
      RunFactorBinding b = new RunFactorBinding();
      b.setRunId(run.getId());
      b.setFactorVersionId(v.getId());
      b.setFactorCode(f.getCode());
      b.setRegion(f.getRegion());
      b.setEnergyType(f.getEnergyType());
      b.setValue(v.getValue());
      b.setValidFrom(v.getValidFrom());
      b.setValidTo(v.getValidTo());
      bindingRepo.save(b);
    }
  }

  private record GroupKey(Long plantId, Long productLineId, String energyType) {}

  /** 执行核算：先逐行生成测量换算证明，再仅使用启动时绑定的因子清单；结果与来源行建立血缘。 */
  @Transactional
  public void execute(Long runId) {
    CalcRun run = runRepo.findById(runId).orElseThrow(() -> BizException.notFound("运行不存在"));
    if (run.getStatus() != RunStatus.RUNNING) return;
    try {
      // 1) 测量换算证明：每段活动量连接到可用换算链（四类状态 + 不确定范围 + 冲突保留）
      conversionService.proveConversions(runId, run.getPeriod());

      List<RunFactorBinding> bindings = bindingRepo.findByRunId(runId);
      List<SourceRow> rows = rowRepo.findActiveVerifiedByPeriod(run.getPeriod(), RowStatus.ACTIVE);
      Map<Long, Plant> plants = plantRepo.findAll().stream()
          .collect(Collectors.toMap(Plant::getId, p -> p));
      // 换算证明按来源行索引：因子按规范单位口径作用于换算后的活动量
      Map<Long, ActivityConversion> convByRow = new LinkedHashMap<>();
      for (ActivityConversion c : conversionService.conversionsOfRun(runId)) {
        convByRow.put(c.getSourceRowId(), c);
      }

      Map<GroupKey, List<SourceRow>> groups = new LinkedHashMap<>();
      for (SourceRow r : rows) {
        groups.computeIfAbsent(new GroupKey(r.getPlantId(), r.getProductLineId(), r.getEnergyType()),
            k -> new ArrayList<>()).add(r);
      }

      for (Map.Entry<GroupKey, List<SourceRow>> e : groups.entrySet()) {
        GroupKey key = e.getKey();
        Plant plant = plants.get(key.plantId());
        if (plant == null) throw BizException.badRequest("来源行引用了不存在的工厂: " + key.plantId());

        // 规范因子单位活动量：优先取换算证明的规范量；同单位恒等时与原始数量一致
        BigDecimal qty = BigDecimal.ZERO;
        boolean convOpen = false;
        for (SourceRow sr : e.getValue()) {
          ActivityConversion ac = convByRow.get(sr.getId());
          if (ac != null && ac.getConvStatus() == ConversionStatus.CONVERTIBLE
              && ac.getCanonicalQty() != null) {
            qty = qty.add(ac.getCanonicalQty());
          } else {
            qty = qty.add(sr.getQuantity()); // 未闭合时保留原始量用于展示，但结果将标记待核
            convOpen = true;
          }
        }

        RunFactorBinding binding = bindings.stream()
            .filter(b -> b.getEnergyType().equals(key.energyType())
                && b.getRegion().equals(plant.getRegion()))
            .findFirst().orElse(null);

        EmissionResult res = new EmissionResult();
        res.setRunId(runId);
        res.setPlantId(key.plantId());
        res.setProductLineId(key.productLineId());
        res.setScope(EnergyTypes.scopeOf(key.energyType()));
        res.setEnergyType(key.energyType());
        res.setActivityQty(qty);
        res.setUnit(EnergyTypes.unitOf(key.energyType()));
        if (convOpen) {
          // 换算链未闭合（人工桥接/量纲不相容/证据不足）优先标记：即便因子齐备也不得计算排放
          res.setStatus(ResultStatus.PENDING_CONVERSION);
          if (binding != null) {
            res.setFactorVersionId(binding.getFactorVersionId());
            res.setFactorValue(binding.getValue());
          }
        } else if (binding == null) {
          // 缺少适用批准因子：指标列为待核，不用集团平均因子兜底
          res.setStatus(ResultStatus.PENDING_FACTOR);
        } else {
          res.setFactorVersionId(binding.getFactorVersionId());
          res.setFactorValue(binding.getValue());
          res.setEmissionTco2e(qty.multiply(binding.getValue())
              .divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP));
          res.setStatus(ResultStatus.OK);
        }
        resultRepo.save(res);
        for (SourceRow row : e.getValue()) {
          lineageRepo.save(new EmissionLineage(res.getId(), row.getId(), row.getQuantity()));
        }
      }
      // 2) 证明回填结果、生成派生指标（能量/排放/强度，含区间与空分母标记）
      conversionService.linkResults(runId);
      conversionService.buildDerivedMetrics(runId);
      run.setStatus(RunStatus.COMPLETED);
      run.setCompletedAt(LocalDateTime.now());
      runRepo.save(run);
    } catch (Exception ex) {
      run.setStatus(RunStatus.FAILED);
      run.setError(ex.getMessage());
      run.setCompletedAt(LocalDateTime.now());
      runRepo.save(run);
    }
  }

  // ---------------- 查询 ----------------

  public List<CalcRun> listRuns() { return runRepo.findAllByOrderByIdDesc(); }

  public CalcRun getRun(Long id) {
    return runRepo.findById(id).orElseThrow(() -> BizException.notFound("运行不存在: " + id));
  }

  public List<RunFactorBinding> bindingsOf(Long runId) { return bindingRepo.findByRunId(runId); }

  public List<EmissionResult> resultsOf(Long runId) { return resultRepo.findByRunIdOrderById(runId); }

  /** 血缘下钻：某条排放结果 ← 哪份来源行（表计/凭证）+ 哪版因子。 */
  public Map<String, Object> lineageOf(Long resultId) {
    EmissionResult res = resultRepo.findById(resultId)
        .orElseThrow(() -> BizException.notFound("排放结果不存在: " + resultId));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("result", res);

    Map<String, Object> factorInfo = null;
    if (res.getFactorVersionId() != null) {
      FactorVersion v = versionRepo.findById(res.getFactorVersionId()).orElse(null);
      if (v != null) {
        Factor f = factorRepo.findById(v.getFactorId()).orElse(null);
        factorInfo = new LinkedHashMap<>();
        factorInfo.put("factorCode", f != null ? f.getCode() : null);
        factorInfo.put("factorName", f != null ? f.getName() : null);
        factorInfo.put("region", f != null ? f.getRegion() : null);
        factorInfo.put("versionId", v.getId());
        factorInfo.put("versionNo", v.getVersionNo());
        factorInfo.put("value", v.getValue());
        factorInfo.put("validFrom", v.getValidFrom());
        factorInfo.put("validTo", v.getValidTo());
        factorInfo.put("status", v.getStatus());
        factorInfo.put("approvedBy", v.getApprovedBy());
        factorInfo.put("approvedAt", v.getApprovedAt());
      }
    }
    out.put("factorVersion", factorInfo);

    List<EmissionLineage> edges = lineageRepo.findByResultId(resultId);
    List<Map<String, Object>> sources = new ArrayList<>();
    for (EmissionLineage edge : edges) {
      SourceRow row = rowRepo.findById(edge.getSourceRowId()).orElse(null);
      if (row == null) continue;
      SourceBatch batch = batchRepo.findById(row.getBatchId()).orElse(null);
      Map<String, Object> s = new LinkedHashMap<>();
      s.put("rowId", row.getId());
      s.put("rowNo", row.getRowNo());
      s.put("batchId", row.getBatchId());
      s.put("batchNo", batch != null ? batch.getBatchNo() : null);
      s.put("batchStatus", batch != null ? batch.getStatus() : null);
      s.put("verifiedBy", batch != null ? batch.getVerifiedBy() : null);
      s.put("verifiedAt", batch != null ? batch.getVerifiedAt() : null);
      s.put("uploadedBy", batch != null ? batch.getUploadedBy() : null);
      s.put("voucherNo", row.getVoucherNo());
      s.put("quantity", row.getQuantity());
      s.put("qtyUsed", edge.getQtyUsed());
      s.put("unit", row.getUnit());
      s.put("rowStatus", row.getStatus());
      sources.add(s);
    }
    out.put("sources", sources);
    return out;
  }

  public static String newNo(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }
}
