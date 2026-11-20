package com.group.carbon.model;

import com.group.carbon.common.Types.ImpactStatus;
import com.group.carbon.common.Types.TriggerType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "impact_assessments")
public class ImpactAssessment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", nullable = false)
  private TriggerType triggerType;
  @Column(name = "trigger_ref", nullable = false)
  private String triggerRef;
  @Column(nullable = false)
  private String period;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ImpactStatus status = ImpactStatus.OPEN;
  @Column
  private String summary;
  @Column(name = "candidate_run_id")
  private Long candidateRunId;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public TriggerType getTriggerType() { return triggerType; }
  public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
  public String getTriggerRef() { return triggerRef; }
  public void setTriggerRef(String triggerRef) { this.triggerRef = triggerRef; }
  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }
  public ImpactStatus getStatus() { return status; }
  public void setStatus(ImpactStatus status) { this.status = status; }
  public String getSummary() { return summary; }
  public void setSummary(String summary) { this.summary = summary; }
  public Long getCandidateRunId() { return candidateRunId; }
  public void setCandidateRunId(Long candidateRunId) { this.candidateRunId = candidateRunId; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
