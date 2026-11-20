package com.group.carbon.model;

import com.group.carbon.common.Types.EdgeKind;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** 有向换算边：每次单位转换一条带量纲、条件与区间误差的关系，核算时逐边求值。 */
@Entity
@Table(name = "conversion_edges")
public class ConversionEdge {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "base_id", nullable = false)
  private Long baseId;
  @Column(name = "from_dimension", nullable = false)
  private String fromDimension;
  @Column(name = "to_dimension", nullable = false)
  private String toDimension;
  @Column(name = "from_unit", nullable = false)
  private String fromUnit;
  @Column(name = "to_unit", nullable = false)
  private String toUnit;
  @Column(name = "from_basis")
  private String fromBasis;
  @Column(name = "to_basis")
  private String toBasis;
  @Enumerated(EnumType.STRING)
  @Column(name = "edge_kind", nullable = false)
  private EdgeKind edgeKind;
  @Column(name = "condition_key")
  private String conditionKey;
  @Column(name = "condition_value")
  private String conditionValue;
  @Column(name = "factor_value", nullable = false)
  private BigDecimal factorValue;
  @Column(name = "uncertainty_pct", nullable = false)
  private BigDecimal uncertaintyPct;
  @Column(name = "evidence_id")
  private Long evidenceId;
  @Column(nullable = false)
  private String status = "APPROVED";

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getBaseId() { return baseId; }
  public void setBaseId(Long baseId) { this.baseId = baseId; }
  public String getFromDimension() { return fromDimension; }
  public void setFromDimension(String fromDimension) { this.fromDimension = fromDimension; }
  public String getToDimension() { return toDimension; }
  public void setToDimension(String toDimension) { this.toDimension = toDimension; }
  public String getFromUnit() { return fromUnit; }
  public void setFromUnit(String fromUnit) { this.fromUnit = fromUnit; }
  public String getToUnit() { return toUnit; }
  public void setToUnit(String toUnit) { this.toUnit = toUnit; }
  public String getFromBasis() { return fromBasis; }
  public void setFromBasis(String fromBasis) { this.fromBasis = fromBasis; }
  public String getToBasis() { return toBasis; }
  public void setToBasis(String toBasis) { this.toBasis = toBasis; }
  public EdgeKind getEdgeKind() { return edgeKind; }
  public void setEdgeKind(EdgeKind edgeKind) { this.edgeKind = edgeKind; }
  public String getConditionKey() { return conditionKey; }
  public void setConditionKey(String conditionKey) { this.conditionKey = conditionKey; }
  public String getConditionValue() { return conditionValue; }
  public void setConditionValue(String conditionValue) { this.conditionValue = conditionValue; }
  public BigDecimal getFactorValue() { return factorValue; }
  public void setFactorValue(BigDecimal factorValue) { this.factorValue = factorValue; }
  public BigDecimal getUncertaintyPct() { return uncertaintyPct; }
  public void setUncertaintyPct(BigDecimal uncertaintyPct) { this.uncertaintyPct = uncertaintyPct; }
  public Long getEvidenceId() { return evidenceId; }
  public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
