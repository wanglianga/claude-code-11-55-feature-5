package com.group.carbon.model;

import com.group.carbon.common.Types.RestatementStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 重述建议：已发布报告受来源/因子变化影响时生成；接受后冻结新版本，绝不覆盖已发布版本。 */
@Entity
@Table(name = "restatements")
public class Restatement {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "report_id", nullable = false)
  private Long reportId;
  @Column(name = "impact_id", nullable = false)
  private Long impactId;
  @Column
  private String reason;
  @Column(name = "diff_summary")
  private String diffSummary;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RestatementStatus status = RestatementStatus.PROPOSED;
  @Column(name = "candidate_run_id")
  private Long candidateRunId;
  @Column(name = "new_version_id")
  private Long newVersionId;
  @Column(name = "created_by")
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getReportId() { return reportId; }
  public void setReportId(Long reportId) { this.reportId = reportId; }
  public Long getImpactId() { return impactId; }
  public void setImpactId(Long impactId) { this.impactId = impactId; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public String getDiffSummary() { return diffSummary; }
  public void setDiffSummary(String diffSummary) { this.diffSummary = diffSummary; }
  public RestatementStatus getStatus() { return status; }
  public void setStatus(RestatementStatus status) { this.status = status; }
  public Long getCandidateRunId() { return candidateRunId; }
  public void setCandidateRunId(Long candidateRunId) { this.candidateRunId = candidateRunId; }
  public Long getNewVersionId() { return newVersionId; }
  public void setNewVersionId(Long newVersionId) { this.newVersionId = newVersionId; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getResolvedAt() { return resolvedAt; }
  public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
