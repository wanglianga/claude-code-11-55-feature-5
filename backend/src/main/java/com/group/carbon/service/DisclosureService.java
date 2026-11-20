package com.group.carbon.service;

import com.group.carbon.common.BizException;
import com.group.carbon.common.Types.*;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 披露服务：把运行结果冻结为报告版本。
 * - 勾稽：产品线合计必须与工厂明细一致；存在未分摊或待核指标时报告保持“待核”，不允许把差额归零；
 * - 审批/发布前强制校验勾稽与待核状态；
 * - 已发布版本内容不可改写，重述只能产生新版本。
 */
@Service
public class DisclosureService {
  private final ReportRepo reportRepo;
  private final ReportVersionRepo versionRepo;
  private final ReportItemRepo itemRepo;
  private final ReconciliationRepo reconRepo;
  private final EmissionResultRepo resultRepo;
  private final CalcRunRepo runRepo;
  private final ProductLineRepo lineRepo;
  private final RestatementRepo restatementRepo;
  private final ImpactAssessmentRepo impactRepo;
  private final ConversionService conversionService;

  public DisclosureService(ReportRepo reportRepo, ReportVersionRepo versionRepo,
                           ReportItemRepo itemRepo, ReconciliationRepo reconRepo,
                           EmissionResultRepo resultRepo, CalcRunRepo runRepo,
                           ProductLineRepo lineRepo, RestatementRepo restatementRepo,
                           ImpactAssessmentRepo impactRepo,
                           @Lazy ConversionService conversionService) {
    this.reportRepo = reportRepo;
    this.versionRepo = versionRepo;
    this.itemRepo = itemRepo;
    this.reconRepo = reconRepo;
    this.resultRepo = resultRepo;
    this.runRepo = runRepo;
    this.lineRepo = lineRepo;
    this.restatementRepo = restatementRepo;
    this.impactRepo = impactRepo;
    this.conversionService = conversionService;
  }

  public List<Report> listReports() { return reportRepo.findAllByOrderByIdDesc(); }

  public Map<String, Object> detail(Long reportId) {
    Report report = getReport(reportId);
    List<ReportVersion> versions = versionRepo.findByReportIdOrderByVersionNoDesc(reportId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("report", report);
    out.put("versions", versions);
    out.put("restatements", restatementRepo.findByReportIdOrderByIdDesc(reportId));
    if (!versions.isEmpty()) {
      ReportVersion latest = versions.get(0);
      out.put("items", itemRepo.findByReportVersionIdOrderById(latest.getId()));
      out.put("reconciliations", reconRepo.findByReportVersionIdOrderById(latest.getId()));
      out.put("cellTraces", conversionService.tracesOfVersion(latest.getId()));
    }
    return out;
  }

  public Map<String, Object> versionDetail(Long versionId) {
    ReportVersion v = versionRepo.findById(versionId)
        .orElseThrow(() -> BizException.notFound("报告版本不存在: " + versionId));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("version", v);
    out.put("items", itemRepo.findByReportVersionIdOrderById(versionId));
    out.put("reconciliations", reconRepo.findByReportVersionIdOrderById(versionId));
    out.put("cellTraces", conversionService.tracesOfVersion(versionId));
    return out;
  }

  private Report getReport(Long id) {
    return reportRepo.findById(id).orElseThrow(() -> BizException.notFound("报告不存在: " + id));
  }

  // ---------------- 创建与冻结 ----------------

  @Transactional
  public Report createReport(String username, String title, String period, Long runId) {
    RunService.validatePeriod(period);
    Report report = new Report();
    report.setCode(RunService.newNo("RPT"));
    report.setTitle(title);
    report.setPeriod(period);
    report.setStatus(ReportStatus.DRAFT);
    report.setCreatedBy(username);
    reportRepo.save(report);
    freeze(report.getId(), runId, username, null, null);
    return reportRepo.findById(report.getId()).orElseThrow();
  }

  /** 冻结：把某次运行的结果固化为新的报告版本，并执行勾稽检查。 */
  @Transactional
  public ReportVersion freeze(Long reportId, Long runId, String username,
                              Long restatementOfVersionId, String note) {
    Report report = reportRepo.findByIdForUpdate(reportId)
        .orElseThrow(() -> BizException.notFound("报告不存在: " + reportId));
    CalcRun run = runRepo.findById(runId)
        .orElseThrow(() -> BizException.notFound("运行不存在: " + runId));
    if (run.getStatus() != RunStatus.COMPLETED) {
      throw BizException.conflict("只能冻结“已完成”状态的运行，当前状态: " + run.getStatus());
    }
    if (!run.getPeriod().equals(report.getPeriod())) {
      throw BizException.badRequest("运行期间 " + run.getPeriod() + " 与报告期间 " + report.getPeriod() + " 不一致");
    }

    List<ReportVersion> existing = versionRepo.findByReportIdOrderByVersionNoDesc(reportId);
    int versionNo = existing.stream().mapToInt(ReportVersion::getVersionNo).max().orElse(0) + 1;
    for (ReportVersion v : existing) {
      if (v.getStatus() == VersionStatus.FROZEN) {
        v.setStatus(VersionStatus.SUPERSEDED);
        versionRepo.save(v);
      }
    }

    ReportVersion version = new ReportVersion();
    version.setReportId(reportId);
    version.setVersionNo(versionNo);
    version.setRunId(runId);
    version.setStatus(VersionStatus.FROZEN);
    version.setFrozenBy(username);
    version.setFrozenAt(LocalDateTime.now());
    version.setRestatementOfVersionId(restatementOfVersionId);
    version.setNote(note);

    // 1) 汇总运行结果为披露条目（工厂 × 产品线 × 范围 × 能源类型）
    List<EmissionResult> results = resultRepo.findByRunIdOrderById(runId);
    record AggKey(Long plantId, Long productLineId, String scope, String energyType) {}
    Map<AggKey, List<EmissionResult>> grouped = new LinkedHashMap<>();
    for (EmissionResult r : results) {
      grouped.computeIfAbsent(new AggKey(r.getPlantId(), r.getProductLineId(), r.getScope(), r.getEnergyType()),
          k -> new ArrayList<>()).add(r);
    }

    versionRepo.save(version);
    List<ReportItem> items = new ArrayList<>();
    for (Map.Entry<AggKey, List<EmissionResult>> e : grouped.entrySet()) {
      AggKey key = e.getKey();
      ReportItem item = new ReportItem();
      item.setReportVersionId(version.getId());
      item.setPlantId(key.plantId());
      item.setProductLineId(key.productLineId());
      item.setScope(key.scope());
      item.setEnergyType(key.energyType());
      item.setActivityQty(e.getValue().stream()
          .map(EmissionResult::getActivityQty).reduce(BigDecimal.ZERO, BigDecimal::add));
      boolean pendingFactor = e.getValue().stream().anyMatch(r -> r.getStatus() == ResultStatus.PENDING_FACTOR);
      boolean pendingConv = e.getValue().stream().anyMatch(r -> r.getStatus() == ResultStatus.PENDING_CONVERSION);
      item.setItemStatus(pendingFactor ? ResultStatus.PENDING_FACTOR
          : (pendingConv ? ResultStatus.PENDING_CONVERSION : ResultStatus.OK));
      item.setEmissionTco2e((pendingFactor || pendingConv) ? null : e.getValue().stream()
          .map(r -> r.getEmissionTco2e() == null ? BigDecimal.ZERO : r.getEmissionTco2e())
          .reduce(BigDecimal.ZERO, BigDecimal::add));
      items.add(itemRepo.save(item));
    }

    // 2) 总量（仅统计正常条目；待核条目不估值、不用平均数兜底）
    BigDecimal s1 = sumScope(items, "SCOPE1");
    BigDecimal s2 = sumScope(items, "SCOPE2");
    BigDecimal s3 = sumScope(items, "SCOPE3");
    version.setTotalScope1(s1);
    version.setTotalScope2(s2);
    version.setTotalScope3(s3);
    version.setTotalTco2e(s1.add(s2).add(s3));

    // 3) 勾稽：工厂明细合计 vs 产品线合计（缺因子与换算未闭合均列为待核）
    int pendingCount = (int) items.stream()
        .filter(i -> i.getItemStatus() == ResultStatus.PENDING_FACTOR
            || i.getItemStatus() == ResultStatus.PENDING_CONVERSION).count();
    version.setPendingCount(pendingCount);
    BigDecimal grandByPlant = items.stream()
        .map(i -> i.getEmissionTco2e() == null ? BigDecimal.ZERO : i.getEmissionTco2e())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal grandAllocated = items.stream()
        .filter(i -> i.getProductLineId() != null)
        .map(i -> i.getEmissionTco2e() == null ? BigDecimal.ZERO : i.getEmissionTco2e())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal unallocated = grandByPlant.subtract(grandAllocated);

    Map<Long, String> lineNames = lineRepo.findAll().stream()
        .collect(Collectors.toMap(ProductLine::getId, ProductLine::getName));
    Set<Long> lineIds = items.stream().map(ReportItem::getProductLineId)
        .filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
    for (Long lineId : lineIds) {
      BigDecimal lineTotal = items.stream()
          .filter(i -> lineId.equals(i.getProductLineId()))
          .map(i -> i.getEmissionTco2e() == null ? BigDecimal.ZERO : i.getEmissionTco2e())
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      saveRecon(version.getId(), lineId, "产品线合计 - " + lineNames.getOrDefault(lineId, String.valueOf(lineId)),
          lineTotal, lineTotal, BigDecimal.ZERO, "OK");
    }
    if (unallocated.compareTo(BigDecimal.ZERO) != 0) {
      saveRecon(version.getId(), null, "未分摊到产品线的工厂级排放",
          unallocated, BigDecimal.ZERO, unallocated, "MISMATCH");
    }
    if (pendingCount > 0) {
      saveRecon(version.getId(), null, "缺少适用批准因子的待核指标（" + pendingCount + " 项）",
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "PENDING_FACTOR");
    }

    ReconcileStatus reconcile = unallocated.compareTo(BigDecimal.ZERO) != 0
        ? ReconcileStatus.MISMATCH
        : (pendingCount > 0 ? ReconcileStatus.PENDING_FACTOR : ReconcileStatus.OK);
    version.setReconcileStatus(reconcile);
    version.setReconcileDiff(unallocated);
    versionRepo.save(version);

    // 4) 披露单元格追溯：能量/排放/强度单元格附可复算换算路径与不确定范围
    conversionService.traceVersion(version, items);

    report.setStatus(reconcile == ReconcileStatus.OK ? ReportStatus.PENDING_REVIEW : ReportStatus.RECONCILE_FAILED);
    reportRepo.save(report);
    return version;
  }

  private void saveRecon(Long versionId, Long lineId, String label,
                         BigDecimal plantSum, BigDecimal lineTotal, BigDecimal diff, String status) {
    Reconciliation r = new Reconciliation();
    r.setReportVersionId(versionId);
    r.setProductLineId(lineId);
    r.setLabel(label);
    r.setPlantDetailSum(plantSum);
    r.setProductLineTotal(lineTotal);
    r.setDiff(diff);
    r.setStatus(status);
    reconRepo.save(r);
  }

  private static BigDecimal sumScope(List<ReportItem> items, String scope) {
    return items.stream()
        .filter(i -> scope.equals(i.getScope()) && i.getEmissionTco2e() != null)
        .map(ReportItem::getEmissionTco2e).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // ---------------- 审批与发布 ----------------

  @Transactional
  public Report approve(Long reportId, String username) {
    Report report = reportRepo.findByIdForUpdate(reportId)
        .orElseThrow(() -> BizException.notFound("报告不存在: " + reportId));
    if (report.getStatus() != ReportStatus.PENDING_REVIEW) {
      throw BizException.conflict("只有“待复核”状态的报告可以审批，当前状态: " + report.getStatus());
    }
    ReportVersion latest = latestVersion(reportId);
    requireReconciled(latest);
    report.setStatus(ReportStatus.APPROVED);
    return reportRepo.save(report);
  }

  @Transactional
  public Report publish(Long reportId, String username) {
    Report report = reportRepo.findByIdForUpdate(reportId)
        .orElseThrow(() -> BizException.notFound("报告不存在: " + reportId));
    if (report.getStatus() != ReportStatus.APPROVED) {
      throw BizException.conflict("只有“已审批”状态的报告可以发布，当前状态: " + report.getStatus());
    }
    ReportVersion latest = latestVersion(reportId);
    requireReconciled(latest);

    versionRepo.findFirstByReportIdAndStatusOrderByVersionNoDesc(reportId, VersionStatus.PUBLISHED)
        .ifPresent(prev -> {
          // 旧发布版本永不删除/改写内容，仅流转状态保留披露历史
          boolean isRestatement = latest.getRestatementOfVersionId() != null
              && latest.getRestatementOfVersionId().equals(prev.getId());
          prev.setStatus(isRestatement ? VersionStatus.RESTATED : VersionStatus.SUPERSEDED);
          // 立即 flush，保证“旧版本先下线”先于“新版本上线”落库（部分唯一索引约束）
          versionRepo.saveAndFlush(prev);
        });

    latest.setStatus(VersionStatus.PUBLISHED);
    latest.setPublishedAt(LocalDateTime.now());
    versionRepo.save(latest);
    report.setStatus(ReportStatus.PUBLISHED);
    reportRepo.save(report);

    // 若本次发布是重述结果：关闭对应重述建议与影响评估
    for (Restatement r : restatementRepo.findByNewVersionId(latest.getId())) {
      r.setStatus(RestatementStatus.RESOLVED);
      r.setResolvedAt(LocalDateTime.now());
      restatementRepo.save(r);
      impactRepo.findById(r.getImpactId()).ifPresent(impact -> {
        impact.setStatus(ImpactStatus.RESOLVED);
        impactRepo.save(impact);
      });
    }
    return report;
  }

  private ReportVersion latestVersion(Long reportId) {
    return versionRepo.findByReportIdOrderByVersionNoDesc(reportId).stream()
        .findFirst().orElseThrow(() -> BizException.conflict("报告尚未冻结任何版本"));
  }

  /** 待核/勾稽不平时禁止推进：差额不能为了按期提交而归零。 */
  private void requireReconciled(ReportVersion version) {
    if (version.getReconcileStatus() == ReconcileStatus.MISMATCH) {
      throw BizException.conflict("产品线合计与工厂明细不勾稽（差异 "
          + version.getReconcileDiff().toPlainString() + " tCO2e），报告保持待核，不能推进审批/发布");
    }
    if (version.getReconcileStatus() == ReconcileStatus.PENDING_FACTOR || version.getPendingCount() > 0) {
      throw BizException.conflict("存在 " + version.getPendingCount()
          + " 项缺少适用批准因子的待核指标，报告保持待核，不能推进审批/发布");
    }
  }
}
