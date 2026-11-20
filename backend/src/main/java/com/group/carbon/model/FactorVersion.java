package com.group.carbon.model;

import com.group.carbon.common.Types.FactorStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "factor_versions")
public class FactorVersion {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "factor_id", nullable = false)
  private Long factorId;
  @Column(name = "version_no", nullable = false)
  private int versionNo;
  /** kgCO2e / 单位活动量 */
  @Column(nullable = false)
  private BigDecimal value;
  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;
  @Column(name = "valid_to")
  private LocalDate validTo;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private FactorStatus status = FactorStatus.DRAFT;
  @Column(name = "created_by", nullable = false)
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
  @Column(name = "approved_by")
  private String approvedBy;
  @Column(name = "approved_at")
  private LocalDateTime approvedAt;
  @Column(name = "replaces_version_id")
  private Long replacesVersionId;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getFactorId() { return factorId; }
  public void setFactorId(Long factorId) { this.factorId = factorId; }
  public int getVersionNo() { return versionNo; }
  public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
  public BigDecimal getValue() { return value; }
  public void setValue(BigDecimal value) { this.value = value; }
  public LocalDate getValidFrom() { return validFrom; }
  public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
  public LocalDate getValidTo() { return validTo; }
  public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
  public FactorStatus getStatus() { return status; }
  public void setStatus(FactorStatus status) { this.status = status; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public String getApprovedBy() { return approvedBy; }
  public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
  public LocalDateTime getApprovedAt() { return approvedAt; }
  public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
  public Long getReplacesVersionId() { return replacesVersionId; }
  public void setReplacesVersionId(Long replacesVersionId) { this.replacesVersionId = replacesVersionId; }
}
