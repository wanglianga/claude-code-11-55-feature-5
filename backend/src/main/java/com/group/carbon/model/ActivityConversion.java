package com.group.carbon.model;

import com.group.carbon.common.Types.ConversionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 活动量换算证明：每段活动量（来源行）到可用换算链的逐边求值结果。 */
@Entity
@Table(name = "activity_conversions")
public class ActivityConversion {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "run_id", nullable = false)
  private Long runId;
  @Column(name = "source_row_id", nullable = false)
  private Long sourceRowId;
  @Column(name = "result_id")
  private Long resultId;
  @Column(name = "energy_type", nullable = false)
  private String energyType;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(name = "product_line_id")
  private Long productLineId;
  @Column(nullable = false)
  private String period;
  @Column(name = "source_qty", nullable = false)
  private BigDecimal sourceQty;
  @Column(name = "source_unit", nullable = false)
  private String sourceUnit;
  @Column(name = "source_dimension", nullable = false)
  private String sourceDimension;
  @Column(name = "target_qty")
  private BigDecimal targetQty;
  @Column(name = "target_unit")
  private String targetUnit;
  @Column(name = "target_dimension")
  private String targetDimension;
  @Column(name = "canonical_qty")
  private BigDecimal canonicalQty;
  @Column(name = "canonical_unit")
  private String canonicalUnit;
  @Column(name = "canonical_lower")
  private BigDecimal canonicalLower;
  @Column(name = "canonical_upper")
  private BigDecimal canonicalUpper;
  @Enumerated(EnumType.STRING)
  @Column(name = "conv_status", nullable = false)
  private ConversionStatus convStatus;
  @Column(name = "path_json")
  private String pathJson;
  @Column(name = "uncertainty_pct")
  private BigDecimal uncertaintyPct;
  @Column(name = "lower_qty")
  private BigDecimal lowerQty;
  @Column(name = "upper_qty")
  private BigDecimal upperQty;
  @Column(name = "truncate_reason")
  private String truncateReason;
  @Column(name = "conflict_flag", nullable = false)
  private boolean conflictFlag = false;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getRunId() { return runId; }
  public void setRunId(Long runId) { this.runId = runId; }
  public Long getSourceRowId() { return sourceRowId; }
  public void setSourceRowId(Long sourceRowId) { this.sourceRowId = sourceRowId; }
  public Long getResultId() { return resultId; }
  public void setResultId(Long resultId) { this.resultId = resultId; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public Long getProductLineId() { return productLineId; }
  public void setProductLineId(Long productLineId) { this.productLineId = productLineId; }
  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }
  public BigDecimal getSourceQty() { return sourceQty; }
  public void setSourceQty(BigDecimal sourceQty) { this.sourceQty = sourceQty; }
  public String getSourceUnit() { return sourceUnit; }
  public void setSourceUnit(String sourceUnit) { this.sourceUnit = sourceUnit; }
  public String getSourceDimension() { return sourceDimension; }
  public void setSourceDimension(String sourceDimension) { this.sourceDimension = sourceDimension; }
  public BigDecimal getTargetQty() { return targetQty; }
  public void setTargetQty(BigDecimal targetQty) { this.targetQty = targetQty; }
  public String getTargetUnit() { return targetUnit; }
  public void setTargetUnit(String targetUnit) { this.targetUnit = targetUnit; }
  public String getTargetDimension() { return targetDimension; }
  public void setTargetDimension(String targetDimension) { this.targetDimension = targetDimension; }
  public BigDecimal getCanonicalQty() { return canonicalQty; }
  public void setCanonicalQty(BigDecimal canonicalQty) { this.canonicalQty = canonicalQty; }
  public String getCanonicalUnit() { return canonicalUnit; }
  public void setCanonicalUnit(String canonicalUnit) { this.canonicalUnit = canonicalUnit; }
  public BigDecimal getCanonicalLower() { return canonicalLower; }
  public void setCanonicalLower(BigDecimal canonicalLower) { this.canonicalLower = canonicalLower; }
  public BigDecimal getCanonicalUpper() { return canonicalUpper; }
  public void setCanonicalUpper(BigDecimal canonicalUpper) { this.canonicalUpper = canonicalUpper; }
  public ConversionStatus getConvStatus() { return convStatus; }
  public void setConvStatus(ConversionStatus convStatus) { this.convStatus = convStatus; }
  public String getPathJson() { return pathJson; }
  public void setPathJson(String pathJson) { this.pathJson = pathJson; }
  public BigDecimal getUncertaintyPct() { return uncertaintyPct; }
  public void setUncertaintyPct(BigDecimal uncertaintyPct) { this.uncertaintyPct = uncertaintyPct; }
  public BigDecimal getLowerQty() { return lowerQty; }
  public void setLowerQty(BigDecimal lowerQty) { this.lowerQty = lowerQty; }
  public BigDecimal getUpperQty() { return upperQty; }
  public void setUpperQty(BigDecimal upperQty) { this.upperQty = upperQty; }
  public String getTruncateReason() { return truncateReason; }
  public void setTruncateReason(String truncateReason) { this.truncateReason = truncateReason; }
  public boolean isConflictFlag() { return conflictFlag; }
  public void setConflictFlag(boolean conflictFlag) { this.conflictFlag = conflictFlag; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
