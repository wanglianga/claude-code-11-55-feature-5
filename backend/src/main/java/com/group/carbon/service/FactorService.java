package com.group.carbon.service;

import com.group.carbon.common.BizException;
import com.group.carbon.common.EnergyTypes;
import com.group.carbon.common.Types.*;
import com.group.carbon.dto.Requests.CreateFactorReq;
import com.group.carbon.dto.Requests.CreateVersionReq;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 因子目录：维护地域、适用期与审批版本。
 * 批准新版本时，与其适用期重叠的旧批准版本被替换（REPLACED），
 * 已被运行绑定的旧版本触发影响评估；运行中的核算仍绑定启动时清单。
 */
@Service
public class FactorService {
  private final FactorRepo factorRepo;
  private final FactorVersionRepo versionRepo;
  private final RunFactorBindingRepo bindingRepo;
  private final CalcRunRepo runRepo;
  private final ReportVersionRepo reportVersionRepo;
  private final ImpactService impactService;

  public FactorService(FactorRepo factorRepo, FactorVersionRepo versionRepo,
                       RunFactorBindingRepo bindingRepo, CalcRunRepo runRepo,
                       ReportVersionRepo reportVersionRepo, @Lazy ImpactService impactService) {
    this.factorRepo = factorRepo;
    this.versionRepo = versionRepo;
    this.bindingRepo = bindingRepo;
    this.runRepo = runRepo;
    this.reportVersionRepo = reportVersionRepo;
    this.impactService = impactService;
  }

  public List<Map<String, Object>> listFactors() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Factor f : factorRepo.findAll()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("factor", f);
      List<FactorVersion> versions = versionRepo.findByFactorIdOrderByVersionNoDesc(f.getId());
      m.put("versions", versions);
      m.put("currentApproved", versions.stream()
          .filter(v -> v.getStatus() == FactorStatus.APPROVED).findFirst().orElse(null));
      out.add(m);
    }
    out.sort(Comparator.comparing(m -> ((Factor) m.get("factor")).getCode()));
    return out;
  }

  @Transactional
  public Factor createFactor(String username, CreateFactorReq req) {
    EnergyTypes.Meta meta = EnergyTypes.metaOf(req.energyType());
    if (factorRepo.findByCode(req.code()).isPresent()) {
      throw BizException.conflict("因子编码已存在: " + req.code());
    }
    Factor f = new Factor();
    f.setCode(req.code());
    f.setName(req.name());
    f.setScope(meta.scope());
    f.setEnergyType(req.energyType());
    f.setRegion(req.region());
    f.setUnit(meta.unit());
    return factorRepo.save(f);
  }

  @Transactional
  public FactorVersion createVersion(Long factorId, String username, CreateVersionReq req) {
    Factor factor = factorRepo.findById(factorId)
        .orElseThrow(() -> BizException.notFound("因子不存在: " + factorId));
    if (req.value() == null || req.value().compareTo(BigDecimal.ZERO) <= 0) {
      throw BizException.badRequest("因子值必须为正数");
    }
    LocalDate from = parseDate(req.validFrom(), "适用期起");
    LocalDate to = req.validTo() == null || req.validTo().isBlank() ? null : parseDate(req.validTo(), "适用期止");
    if (to != null && to.isBefore(from)) {
      throw BizException.badRequest("适用期止不能早于适用期起");
    }
    int nextNo = versionRepo.findByFactorIdOrderByVersionNoDesc(factorId).stream()
        .mapToInt(FactorVersion::getVersionNo).max().orElse(0) + 1;
    FactorVersion v = new FactorVersion();
    v.setFactorId(factor.getId());
    v.setVersionNo(nextNo);
    v.setValue(req.value());
    v.setValidFrom(from);
    v.setValidTo(to);
    v.setStatus(FactorStatus.DRAFT);
    v.setCreatedBy(username);
    v.setCreatedAt(LocalDateTime.now());
    return versionRepo.save(v);
  }

  /** 批准版本：替换适用期重叠的旧批准版本；被替换版本若已被运行绑定则触发影响评估。 */
  @Transactional
  public FactorVersion approve(Long versionId, String username) {
    FactorVersion v = versionRepo.findByIdForUpdate(versionId)
        .orElseThrow(() -> BizException.notFound("因子版本不存在: " + versionId));
    if (v.getStatus() != FactorStatus.DRAFT) {
      throw BizException.conflict("只有“草稿”状态的版本可以批准，当前状态: " + v.getStatus());
    }
    Factor factor = factorRepo.findById(v.getFactorId()).orElseThrow();

    List<FactorVersion> replaced = new ArrayList<>();
    for (FactorVersion cur : versionRepo.findApprovedForUpdate(v.getFactorId())) {
      boolean overlap = !cur.getValidFrom().isAfter(v.getValidTo() != null ? v.getValidTo() : LocalDate.MAX)
          && !(cur.getValidTo() != null && cur.getValidTo().isBefore(v.getValidFrom()));
      if (overlap) {
        cur.setStatus(FactorStatus.REPLACED);
        versionRepo.save(cur);
        replaced.add(cur);
      }
    }
    v.setStatus(FactorStatus.APPROVED);
    v.setApprovedBy(username);
    v.setApprovedAt(LocalDateTime.now());
    if (!replaced.isEmpty()) {
      v.setReplacesVersionId(replaced.get(0).getId());
    }
    versionRepo.save(v);

    // 影响评估：被替换版本已被哪些运行绑定 → 对应期间重新比较
    Set<String> periods = new TreeSet<>();
    for (FactorVersion old : replaced) {
      for (RunFactorBinding b : bindingRepo.findByFactorVersionId(old.getId())) {
        CalcRun run = runRepo.findById(b.getRunId()).orElse(null);
        if (run != null && run.getStatus() == RunStatus.COMPLETED) {
          periods.add(run.getPeriod());
        }
      }
    }
    for (String period : periods) {
      impactService.assess(period, TriggerType.FACTOR_REVISION,
          String.format("因子 %s 版本替换：v%d → v%d（%s）",
              factor.getCode(),
              replaced.get(0).getVersionNo(), v.getVersionNo(), v.getValue().toPlainString()));
    }
    return v;
  }

  @Transactional
  public FactorVersion retire(Long versionId, String username) {
    FactorVersion v = versionRepo.findByIdForUpdate(versionId)
        .orElseThrow(() -> BizException.notFound("因子版本不存在: " + versionId));
    if (v.getStatus() != FactorStatus.APPROVED && v.getStatus() != FactorStatus.DRAFT) {
      throw BizException.conflict("只有“草稿”或“已批准”状态的版本可以停用");
    }
    v.setStatus(FactorStatus.RETIRED);
    return versionRepo.save(v);
  }

  /** 版本使用追踪：哪些运行绑定了该版本、哪些报告版本基于这些运行。 */
  public Map<String, Object> usageOf(Long versionId) {
    FactorVersion v = versionRepo.findById(versionId)
        .orElseThrow(() -> BizException.notFound("因子版本不存在: " + versionId));
    Factor factor = factorRepo.findById(v.getFactorId()).orElse(null);
    List<RunFactorBinding> bindings = bindingRepo.findByFactorVersionId(versionId);
    List<Map<String, Object>> runs = new ArrayList<>();
    for (RunFactorBinding b : bindings) {
      CalcRun run = runRepo.findById(b.getRunId()).orElse(null);
      if (run == null) continue;
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("runId", run.getId());
      m.put("runNo", run.getRunNo());
      m.put("period", run.getPeriod());
      m.put("purpose", run.getPurpose());
      m.put("status", run.getStatus());
      runs.add(m);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("factor", factor);
    out.put("version", v);
    out.put("runs", runs);
    return out;
  }

  private static LocalDate parseDate(String s, String label) {
    try {
      return LocalDate.parse(s);
    } catch (Exception e) {
      throw BizException.badRequest(label + "格式必须为 YYYY-MM-DD: " + s);
    }
  }
}
