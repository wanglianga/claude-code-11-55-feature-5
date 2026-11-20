package com.group.carbon.service;

import com.group.carbon.common.Types.*;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {
  private final ReportRepo reportRepo;
  private final ReportVersionRepo versionRepo;
  private final ReportItemRepo itemRepo;
  private final CalcRunRepo runRepo;
  private final EmissionResultRepo resultRepo;
  private final ImpactAssessmentRepo impactRepo;
  private final RestatementRepo restatementRepo;
  private final PlantRepo plantRepo;

  public DashboardService(ReportRepo reportRepo, ReportVersionRepo versionRepo,
                          ReportItemRepo itemRepo, CalcRunRepo runRepo,
                          EmissionResultRepo resultRepo, ImpactAssessmentRepo impactRepo,
                          RestatementRepo restatementRepo, PlantRepo plantRepo) {
    this.reportRepo = reportRepo;
    this.versionRepo = versionRepo;
    this.itemRepo = itemRepo;
    this.runRepo = runRepo;
    this.resultRepo = resultRepo;
    this.impactRepo = impactRepo;
    this.restatementRepo = restatementRepo;
    this.plantRepo = plantRepo;
  }

  public Map<String, Object> summary() {
    Map<String, Object> out = new LinkedHashMap<>();

    // 最新已发布版本（披露口径）
    List<ReportVersion> published = versionRepo.findByStatusOrderByPublishedAtDesc(VersionStatus.PUBLISHED);
    if (!published.isEmpty()) {
      ReportVersion v = published.get(0);
      Report report = reportRepo.findById(v.getReportId()).orElse(null);
      Map<String, Object> p = new LinkedHashMap<>();
      p.put("reportCode", report != null ? report.getCode() : null);
      p.put("reportTitle", report != null ? report.getTitle() : null);
      p.put("period", report != null ? report.getPeriod() : null);
      p.put("versionNo", v.getVersionNo());
      p.put("totalScope1", v.getTotalScope1());
      p.put("totalScope2", v.getTotalScope2());
      p.put("totalScope3", v.getTotalScope3());
      p.put("totalTco2e", v.getTotalTco2e());
      p.put("publishedAt", v.getPublishedAt());
      // 按工厂汇总（披露口径）
      Map<Long, BigDecimal> byPlant = new LinkedHashMap<>();
      for (ReportItem i : itemRepo.findByReportVersionIdOrderById(v.getId())) {
        byPlant.merge(i.getPlantId(),
            i.getEmissionTco2e() == null ? BigDecimal.ZERO : i.getEmissionTco2e(), BigDecimal::add);
      }
      Map<Long, String> plantNames = plantRepo.findAll().stream()
          .collect(Collectors.toMap(Plant::getId, Plant::getName));
      p.put("byPlant", byPlant.entrySet().stream().map(e -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plantId", e.getKey());
        m.put("plantName", plantNames.getOrDefault(e.getKey(), "?"));
        m.put("value", e.getValue());
        return m;
      }).toList());
      out.put("latestPublished", p);
    } else {
      out.put("latestPublished", null);
    }

    long openImpacts = impactRepo.findAll().stream().filter(a -> a.getStatus() == ImpactStatus.OPEN).count();
    long reconcileFailed = reportRepo.findAll().stream()
        .filter(r -> r.getStatus() == ReportStatus.RECONCILE_FAILED).count();
    List<CalcRun> runs = runRepo.findAllByOrderByIdDesc();
    CalcRun latestOfficial = runs.stream()
        .filter(r -> r.getPurpose() == RunPurpose.OFFICIAL && r.getStatus() == RunStatus.COMPLETED)
        .findFirst().orElse(null);
    long pendingResults = latestOfficial == null ? 0
        : resultRepo.findByRunIdOrderById(latestOfficial.getId()).stream()
            .filter(r -> r.getStatus() == ResultStatus.PENDING_FACTOR
                || r.getStatus() == ResultStatus.PENDING_CONVERSION).count();

    Map<String, Object> counts = new LinkedHashMap<>();
    counts.put("openImpacts", openImpacts);
    counts.put("reconcileFailedReports", reconcileFailed);
    counts.put("pendingFactorResults", pendingResults);
    counts.put("totalReports", reportRepo.count());
    counts.put("totalRuns", runs.size());
    out.put("counts", counts);

    out.put("recentRuns", runs.stream().limit(6).toList());
    out.put("recentImpacts", impactRepo.findAllByOrderByIdDesc().stream().limit(6).toList());
    return out;
  }
}
