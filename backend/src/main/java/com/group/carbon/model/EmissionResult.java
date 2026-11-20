package com.group.carbon.model;

import com.group.carbon.common.Types.ResultStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "emission_results")
public class EmissionResult {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "run_id", nullable = false)
  private Long runId;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(name = "product_line_id")
  private Long productLineId;
  @Column(nullable = false)
  private String scope;
  @Column(name = "energy_type", nullable = false)
  private String energyType;
  @Column(name = "activity_qty", nullable = false)
  private BigDecimal activityQty;
  @Column(nullable = false)
  private String unit;
  @Column(name = "factor_version_id")
  private Long factorVersionId;
  @Column(name = "factor_value")
  private BigDecimal factorValue;
  @Column(name = "emission_tco2e")
  private BigDecimal emissionTco2e;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ResultStatus status;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getRunId() { return runId; }
  public void setRunId(Long runId) { this.runId = runId; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public Long getProductLineId() { return productLineId; }
  public void setProductLineId(Long productLineId) { this.productLineId = productLineId; }
  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public BigDecimal getActivityQty() { return activityQty; }
  public void setActivityQty(BigDecimal activityQty) { this.activityQty = activityQty; }
  public String getUnit() { return unit; }
  public void setUnit(String unit) { this.unit = unit; }
  public Long getFactorVersionId() { return factorVersionId; }
  public void setFactorVersionId(Long factorVersionId) { this.factorVersionId = factorVersionId; }
  public BigDecimal getFactorValue() { return factorValue; }
  public void setFactorValue(BigDecimal factorValue) { this.factorValue = factorValue; }
  public BigDecimal getEmissionTco2e() { return emissionTco2e; }
  public void setEmissionTco2e(BigDecimal emissionTco2e) { this.emissionTco2e = emissionTco2e; }
  public ResultStatus getStatus() { return status; }
  public void setStatus(ResultStatus status) { this.status = status; }
}
