package com.group.carbon.model;

import com.group.carbon.common.Types.ConversionBaseStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 换算依据：经批准的 物质/能源—单位—工厂—期间 组合。
 * 既是规范转换模板的素材，也为图上每条有向边提供业务含义；
 * 模板不是图路径缓存，图路径也不是模板的事后核查，二者共用同一依据。
 */
@Entity
@Table(name = "conversion_bases")
public class ConversionBase {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false)
  private String code;
  @Column(nullable = false)
  private String name;
  @Column(name = "energy_type", nullable = false)
  private String energyType;
  @Column(name = "plant_id")
  private Long plantId;
  @Column(name = "period_from", nullable = false)
  private String periodFrom;
  @Column(name = "period_to")
  private String periodTo;
  @Column(name = "source_unit", nullable = false)
  private String sourceUnit;
  @Column(name = "source_dimension", nullable = false)
  private String sourceDimension;
  @Column(name = "source_basis")
  private String sourceBasis;
  @Column(name = "target_unit", nullable = false)
  private String targetUnit;
  @Column(name = "target_dimension", nullable = false)
  private String targetDimension;
  @Column(name = "target_basis")
  private String targetBasis;
  @Column(name = "factor_value", nullable = false)
  private BigDecimal factorValue;
  @Column(name = "sig_figs")
  private Integer sigFigs;
  @Column(name = "uncertainty_pct", nullable = false)
  private BigDecimal uncertaintyPct;
  @Column(name = "evidence_id")
  private Long evidenceId;
  @Column(name = "condition_key")
  private String conditionKey;
  @Column(name = "condition_value")
  private String conditionValue;
  @Column(name = "parallel_conflict", nullable = false)
  private boolean parallelConflict = false;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ConversionBaseStatus status = ConversionBaseStatus.DRAFT;
  @Column(name = "supersedes_id")
  private Long supersedesId;
  @Column(name = "created_by", nullable = false)
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
  @Column(name = "approved_by")
  private String approvedBy;
  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public String getPeriodFrom() { return periodFrom; }
  public void setPeriodFrom(String periodFrom) { this.periodFrom = periodFrom; }
  public String getPeriodTo() { return periodTo; }
  public void setPeriodTo(String periodTo) { this.periodTo = periodTo; }
  public String getSourceUnit() { return sourceUnit; }
  public void setSourceUnit(String sourceUnit) { this.sourceUnit = sourceUnit; }
  public String getSourceDimension() { return sourceDimension; }
  public void setSourceDimension(String sourceDimension) { this.sourceDimension = sourceDimension; }
  public String getSourceBasis() { return sourceBasis; }
  public void setSourceBasis(String sourceBasis) { this.sourceBasis = sourceBasis; }
  public String getTargetUnit() { return targetUnit; }
  public void setTargetUnit(String targetUnit) { this.targetUnit = targetUnit; }
  public String getTargetDimension() { return targetDimension; }
  public void setTargetDimension(String targetDimension) { this.targetDimension = targetDimension; }
  public String getTargetBasis() { return targetBasis; }
  public void setTargetBasis(String targetBasis) { this.targetBasis = targetBasis; }
  public BigDecimal getFactorValue() { return factorValue; }
  public void setFactorValue(BigDecimal factorValue) { this.factorValue = factorValue; }
  public Integer getSigFigs() { return sigFigs; }
  public void setSigFigs(Integer sigFigs) { this.sigFigs = sigFigs; }
  public BigDecimal getUncertaintyPct() { return uncertaintyPct; }
  public void setUncertaintyPct(BigDecimal uncertaintyPct) { this.uncertaintyPct = uncertaintyPct; }
  public Long getEvidenceId() { return evidenceId; }
  public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }
  public String getConditionKey() { return conditionKey; }
  public void setConditionKey(String conditionKey) { this.conditionKey = conditionKey; }
  public String getConditionValue() { return conditionValue; }
  public void setConditionValue(String conditionValue) { this.conditionValue = conditionValue; }
  public boolean isParallelConflict() { return parallelConflict; }
  public void setParallelConflict(boolean parallelConflict) { this.parallelConflict = parallelConflict; }
  public ConversionBaseStatus getStatus() { return status; }
  public void setStatus(ConversionBaseStatus status) { this.status = status; }
  public Long getSupersedesId() { return supersedesId; }
  public void setSupersedesId(Long supersedesId) { this.supersedesId = supersedesId; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public String getApprovedBy() { return approvedBy; }
  public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
  public LocalDateTime getApprovedAt() { return approvedAt; }
  public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
}
