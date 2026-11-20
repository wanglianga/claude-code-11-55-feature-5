package com.group.carbon.model;

import com.group.carbon.common.Types.ConflictStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** 换算冲突：同一读数经不同换算链得到不同物理量时保留，不以平均值消除。 */
@Entity
@Table(name = "conversion_conflicts")
public class ConversionConflict {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "source_row_id", nullable = false)
  private Long sourceRowId;
  @Column(name = "run_id", nullable = false)
  private Long runId;
  @Column(name = "energy_type", nullable = false)
  private String energyType;
  @Column(name = "path_a_json", nullable = false)
  private String pathAJson;
  @Column(name = "path_b_json", nullable = false)
  private String pathBJson;
  @Column(name = "qty_a", nullable = false)
  private BigDecimal qtyA;
  @Column(name = "qty_b", nullable = false)
  private BigDecimal qtyB;
  @Column(name = "target_unit", nullable = false)
  private String targetUnit;
  @Column(name = "basis_diff", nullable = false)
  private String basisDiff;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ConflictStatus status = ConflictStatus.OPEN;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getSourceRowId() { return sourceRowId; }
  public void setSourceRowId(Long sourceRowId) { this.sourceRowId = sourceRowId; }
  public Long getRunId() { return runId; }
  public void setRunId(Long runId) { this.runId = runId; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public String getPathAJson() { return pathAJson; }
  public void setPathAJson(String pathAJson) { this.pathAJson = pathAJson; }
  public String getPathBJson() { return pathBJson; }
  public void setPathBJson(String pathBJson) { this.pathBJson = pathBJson; }
  public BigDecimal getQtyA() { return qtyA; }
  public void setQtyA(BigDecimal qtyA) { this.qtyA = qtyA; }
  public BigDecimal getQtyB() { return qtyB; }
  public void setQtyB(BigDecimal qtyB) { this.qtyB = qtyB; }
  public String getTargetUnit() { return targetUnit; }
  public void setTargetUnit(String targetUnit) { this.targetUnit = targetUnit; }
  public String getBasisDiff() { return basisDiff; }
  public void setBasisDiff(String basisDiff) { this.basisDiff = basisDiff; }
  public ConflictStatus getStatus() { return status; }
  public void setStatus(ConflictStatus status) { this.status = status; }
}
