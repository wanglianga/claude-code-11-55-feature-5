package com.group.carbon.service;

import com.group.carbon.common.BizException;
import com.group.carbon.common.Types.*;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 影响服务：来源或因子变化时，
 * 1. 以当前数据生成候选运行；
 * 2. 与已发布报告（或最近一次正式运行）逐指标比较；
 * 3. 已发布报告受影响时生成重述建议与差异说明 —— 不直接覆盖已发布报告。
 */
@Service
public class ImpactService {
  private final ImpactAssessmentRepo impactRepo;
  private final ImpactItemRepo itemRepo;
  private final RestatementRepo restatementRepo;
  private final ReportRepo reportRepo;
  private final ReportVersionRepo versionRepo;
  private final ReportItemRepo reportItemRepo;
  private final EmissionResultRepo resultRepo;
  private final CalcRunRepo runRepo;
  private final PlantRepo plantRepo;
  private final ProductLineRepo lineRepo;
  private final RunService runService;
  private final DisclosureService disclosureService;

  public ImpactService(ImpactAssessmentRepo impactRepo, ImpactItemRepo itemRepo,
                       RestatementRepo restatementRepo, ReportRepo reportRepo,
                       ReportVersionRepo versionRepo, ReportItemRepo reportItemRepo,
                       EmissionResultRepo resultRepo, CalcRunRepo runRepo,
                       PlantRepo plantRepo, ProductLineRepo lineRepo,
                       RunService runService, DisclosureService disclosureService) {
    this.impactRepo = impactRepo;
    this.itemRepo = itemRepo;
    this.restatementRepo = restatementRepo;
    this.reportRepo = reportRepo;
    this.versionRepo = versionRepo;
    this.reportItemRepo = reportItemRepo;
    this.resultRepo = resultRepo;
    this.runRepo = runRepo;
    this.plantRepo = plantRepo;
    this.lineRepo = lineRepo;
    this.runService = runService;
    this.disclosureService = disclosureService;
  }

  private record Key(Long plantId, Long productLineId, String scope) {}

  @Transactional
  public ImpactAssessment assess(String period, TriggerType type, String ref) {
    List<ReportVersion> published = versionRepo.findPublishedByPeriod(period);
    ReportVersion pv = published.isEmpty() ? null : published.get(0);
    CalcRun official = runRepo.findFirstByPeriodAndPurposeAndStatusOrderByIdDesc(
        period, RunPurpose.OFFICIAL, RunStatus.COMPLETED).orElse(null);
    if (pv == null && official == null) return null;

    // 候选运行：用当前生效数据与当前批准因子重算
    CalcRun candidate = runService.createCandidateRun(period, "impact-service");
    Map<Key, BigDecimal> newVals = aggregateResults(candidate.getId());
    Map<Key, BigDecimal> oldVals = pv != null ? aggregateItems(pv.getId()) : aggregateResults(official.getId());

    Map<Long, String> plantNames = plantRepo.findAll().stream()
        .collect(Collectors.toMap(Plant::getId, Plant::getName));
    Map<Long, String> lineNames = lineRepo.findAll().stream()
        .collect(Collectors.toMap(ProductLine::getId, ProductLine::getName));

    Set<Key> keys = new TreeSet<>(Comparator.comparing(k -> k.plantId() + "|" + k.productLineId() + "|" + k.scope()));
    keys.addAll(oldVals.keySet());
    keys.addAll(newVals.keySet());

    ImpactAssessment assessment = new ImpactAssessment();
    assessment.setTriggerType(type);
    assessment.setTriggerRef(ref);
    assessment.setPeriod(period);
    assessment.setStatus(ImpactStatus.OPEN);
    assessment.setCandidateRunId(candidate.getId());
    impactRepo.save(assessment);

    List<ImpactItem> diffs = new ArrayList<>();
    BigDecimal totalDiff = BigDecimal.ZERO;
    for (Key key : keys) {
      BigDecimal oldV = oldVals.getOrDefault(key, BigDecimal.ZERO);
      BigDecimal newV = newVals.getOrDefault(key, BigDecimal.ZERO);
      if (oldV.compareTo(newV) == 0) continue;
      BigDecimal diff = newV.subtract(oldV);
      totalDiff = totalDiff.add(diff);
      ImpactItem item = new ImpactItem();
      item.setImpactId(assessment.getId());
      item.setPlantId(key.plantId());
      item.setProductLineId(key.productLineId());
      item.setScope(key.scope());
      item.setMetric(plantNames.getOrDefault(key.plantId(), "?") + " / "
          + (key.productLineId() != null ? lineNames.getOrDefault(key.productLineId(), "?") : "未分摊")
          + " / " + key.scope());
      item.setOldValue(oldV);
      item.setNewValue(newV);
      item.setDiff(diff);
      if (pv != null) {
        item.setAffectedReportId(pv.getReportId());
        item.setAffectedReportVersionId(pv.getId());
      }
      diffs.add(itemRepo.save(item));
    }

    Report report = pv != null ? reportRepo.findById(pv.getReportId()).orElse(null) : null;
    String summary = diffs.isEmpty() ? "重算后与既有口径无显著差异"
        : String.format("影响指标 %d 项，总差异 %s tCO2e", diffs.size(), totalDiff.toPlainString());
    if (report != null) {
      summary += "；涉及已发布报告 " + report.getCode() + "（v" + pv.getVersionNo() + "）";
    }
    assessment.setSummary(summary);
    impactRepo.save(assessment);

    // 已发布报告受影响 → 生成重述建议（不覆盖已发布报告）
    if (pv != null && !diffs.isEmpty()) {
      Restatement r = new Restatement();
      r.setReportId(pv.getReportId());
      r.setImpactId(assessment.getId());
      r.setReason(ref);
      r.setDiffSummary(summary + "。建议基于候选运行 " + candidate.getRunNo() + " 冻结重述版本。");
      r.setStatus(RestatementStatus.PROPOSED);
      r.setCandidateRunId(candidate.getId());
      r.setCreatedBy("system");
      restatementRepo.save(r);
    }
    return assessment;
  }

  private Map<Key, BigDecimal> aggregateResults(Long runId) {
    Map<Key, BigDecimal> map = new HashMap<>();
    for (EmissionResult r : resultRepo.findByRunIdOrderById(runId)) {
      Key key = new Key(r.getPlantId(), r.getProductLineId(), r.getScope());
      map.merge(key, r.getEmissionTco2e() == null ? BigDecimal.ZERO : r.getEmissionTco2e(), BigDecimal::add);
    }
    return map;
  }

  private Map<Key, BigDecimal> aggregateItems(Long versionId) {
    Map<Key, BigDecimal> map = new HashMap<>();
    for (ReportItem i : reportItemRepo.findByReportVersionIdOrderById(versionId)) {
      Key key = new Key(i.getPlantId(), i.getProductLineId(), i.getScope());
      map.merge(key, i.getEmissionTco2e() == null ? BigDecimal.ZERO : i.getEmissionTco2e(), BigDecimal::add);
    }
    return map;
  }

  // ---------------- 查询与处置 ----------------

  public List<ImpactAssessment> list() { return impactRepo.findAllByOrderByIdDesc(); }

  public Map<String, Object> detail(Long id) {
    ImpactAssessment a = impactRepo.findById(id)
        .orElseThrow(() -> BizException.notFound("影响评估不存在: " + id));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("assessment", a);
    out.put("items", itemRepo.findByImpactIdOrderById(id));
    out.put("restatements", restatementRepo.findByImpactId(id));
    return out;
  }

  /** 接受重述建议：基于候选运行冻结新的报告版本（旧发布版本保持不动，待重新审批发布）。 */
  @Transactional
  public Map<String, Object> acceptRestatement(Long restatementId, String username) {
    Restatement r = restatementRepo.findById(restatementId)
        .orElseThrow(() -> BizException.notFound("重述建议不存在: " + restatementId));
    if (r.getStatus() != RestatementStatus.PROPOSED) {
      throw BizException.conflict("只有“待处理”状态的重述建议可以接受，当前状态: " + r.getStatus());
    }
    Report report = reportRepo.findById(r.getReportId())
        .orElseThrow(() -> BizException.notFound("报告不存在"));
    Long publishedVersionId = versionRepo
        .findFirstByReportIdAndStatusOrderByVersionNoDesc(report.getId(), VersionStatus.PUBLISHED)
        .map(ReportVersion::getId).orElse(null);

    ReportVersion newVersion = disclosureService.freeze(report.getId(), r.getCandidateRunId(),
        username, publishedVersionId, "重述：" + r.getReason());

    r.setStatus(RestatementStatus.ACCEPTED);
    r.setNewVersionId(newVersion.getId());
    restatementRepo.save(r);
    impactRepo.findById(r.getImpactId()).ifPresent(impact -> {
      if (impact.getStatus() == ImpactStatus.OPEN) {
        impact.setStatus(ImpactStatus.ACKNOWLEDGED);
        impactRepo.save(impact);
      }
    });

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("restatement", r);
    out.put("newVersion", newVersion);
    out.put("reportStatus", reportRepo.findById(report.getId()).orElseThrow().getStatus());
    return out;
  }

  @Transactional
  public Restatement rejectRestatement(Long restatementId, String username, String reason) {
    Restatement r = restatementRepo.findById(restatementId)
        .orElseThrow(() -> BizException.notFound("重述建议不存在: " + restatementId));
    if (r.getStatus() != RestatementStatus.PROPOSED) {
      throw BizException.conflict("只有“待处理”状态的重述建议可以驳回");
    }
    r.setStatus(RestatementStatus.REJECTED);
    r.setResolvedAt(LocalDateTime.now());
    if (reason != null && !reason.isBlank()) {
      r.setDiffSummary(r.getDiffSummary() + "｜驳回说明：" + reason);
    }
    return restatementRepo.save(r);
  }
}
