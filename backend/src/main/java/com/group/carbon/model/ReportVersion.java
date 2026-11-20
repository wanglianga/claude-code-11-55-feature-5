package com.group.carbon.model;

import com.group.carbon.common.Types.ReconcileStatus;
import com.group.carbon.common.Types.VersionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 报告版本：某次运行结果的冻结快照；发布后内容不可改写，重述只能产生新版本。 */
@Entity
@Table(name = "report_versions")
public class ReportVersion {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "report_id", nullable = false)
  private Long reportId;
  @Column(name = "version_no", nullable = false)
  private int versionNo;
  @Column(name = "run_id", nullable = false)
  private Long runId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private VersionStatus status = VersionStatus.FROZEN;
  @Column(name = "total_scope1", nullable = false)
  private BigDecimal totalScope1 = BigDecimal.ZERO;
  @Column(name = "total_scope2", nullable = false)
  private BigDecimal totalScope2 = BigDecimal.ZERO;
  @Column(name = "total_scope3", nullable = false)
  private BigDecimal totalScope3 = BigDecimal.ZERO;
  @Column(name = "total_tco2e", nullable = false)
  private BigDecimal totalTco2e = BigDecimal.ZERO;
  @Enumerated(EnumType.STRING)
  @Column(name = "reconcile_status", nullable = false)
  private ReconcileStatus reconcileStatus = ReconcileStatus.OK;
  @Column(name = "reconcile_diff", nullable = false)
  private BigDecimal reconcileDiff = BigDecimal.ZERO;
  @Column(name = "pending_count", nullable = false)
  private int pendingCount = 0;
  @Column(name = "frozen_by", nullable = false)
  private String frozenBy;
  @Column(name = "frozen_at", nullable = false)
  private LocalDateTime frozenAt = LocalDateTime.now();
  @Column(name = "published_at")
  private LocalDateTime publishedAt;
  @Column(name = "restatement_of_version_id")
  private Long restatementOfVersionId;
  @Column
  private String note;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getReportId() { return reportId; }
  public void setReportId(Long reportId) { this.reportId = reportId; }
  public int getVersionNo() { return versionNo; }
  public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
  public Long getRunId() { return runId; }
  public void setRunId(Long runId) { this.runId = runId; }
  public VersionStatus getStatus() { return status; }
  public void setStatus(VersionStatus status) { this.status = status; }
  public BigDecimal getTotalScope1() { return totalScope1; }
  public void setTotalScope1(BigDecimal totalScope1) { this.totalScope1 = totalScope1; }
  public BigDecimal getTotalScope2() { return totalScope2; }
  public void setTotalScope2(BigDecimal totalScope2) { this.totalScope2 = totalScope2; }
  public BigDecimal getTotalScope3() { return totalScope3; }
  public void setTotalScope3(BigDecimal totalScope3) { this.totalScope3 = totalScope3; }
  public BigDecimal getTotalTco2e() { return totalTco2e; }
  public void setTotalTco2e(BigDecimal totalTco2e) { this.totalTco2e = totalTco2e; }
  public ReconcileStatus getReconcileStatus() { return reconcileStatus; }
  public void setReconcileStatus(ReconcileStatus reconcileStatus) { this.reconcileStatus = reconcileStatus; }
  public BigDecimal getReconcileDiff() { return reconcileDiff; }
  public void setReconcileDiff(BigDecimal reconcileDiff) { this.reconcileDiff = reconcileDiff; }
  public int getPendingCount() { return pendingCount; }
  public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
  public String getFrozenBy() { return frozenBy; }
  public void setFrozenBy(String frozenBy) { this.frozenBy = frozenBy; }
  public LocalDateTime getFrozenAt() { return frozenAt; }
  public void setFrozenAt(LocalDateTime frozenAt) { this.frozenAt = frozenAt; }
  public LocalDateTime getPublishedAt() { return publishedAt; }
  public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
  public Long getRestatementOfVersionId() { return restatementOfVersionId; }
  public void setRestatementOfVersionId(Long restatementOfVersionId) { this.restatementOfVersionId = restatementOfVersionId; }
  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }
}
