package com.group.carbon.model;

import com.group.carbon.common.Types.MetricKind;
import com.group.carbon.common.Types.MetricStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** 派生指标：能量、排放量、排放强度（带单位、小数位与不确定边界）。 */
@Entity
@Table(name = "derived_metrics")
public class DerivedMetric {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "run_id", nullable = false)
  private Long runId;
  @Column(name = "result_id")
  private Long resultId;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(name = "product_line_id")
  private Long productLineId;
  @Column(nullable = false)
  private String scope;
  @Column(name = "energy_type", nullable = false)
  private String energyType;
  @Enumerated(EnumType.STRING)
  @Column(name = "metric_kind", nullable = false)
  private MetricKind metricKind;
  @Column(name = "metric_value")
  private BigDecimal metricValue;
  @Column(name = "metric_unit")
  private String metricUnit;
  @Column(name = "display_decimals", nullable = false)
  private int displayDecimals;
  @Column(name = "uncertainty_pct")
  private BigDecimal uncertaintyPct;
  @Column(name = "lower_value")
  private BigDecimal lowerValue;
  @Column(name = "upper_value")
  private BigDecimal upperValue;
  @Enumerated(EnumType.STRING)
  @Column(name = "metric_status", nullable = false)
  private MetricStatus metricStatus;
  @Column(name = "calc_path_json")
  private String calcPathJson;
  private String note;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getRunId() { return runId; }
  public void setRunId(Long runId) { this.runId = runId; }
  public Long getResultId() { return resultId; }
  public void setResultId(Long resultId) { this.resultId = resultId; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public Long getProductLineId() { return productLineId; }
  public void setProductLineId(Long productLineId) { this.productLineId = productLineId; }
  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public MetricKind getMetricKind() { return metricKind; }
  public void setMetricKind(MetricKind metricKind) { this.metricKind = metricKind; }
  public BigDecimal getMetricValue() { return metricValue; }
  public void setMetricValue(BigDecimal metricValue) { this.metricValue = metricValue; }
  public String getMetricUnit() { return metricUnit; }
  public void setMetricUnit(String metricUnit) { this.metricUnit = metricUnit; }
  public int getDisplayDecimals() { return displayDecimals; }
  public void setDisplayDecimals(int displayDecimals) { this.displayDecimals = displayDecimals; }
  public BigDecimal getUncertaintyPct() { return uncertaintyPct; }
  public void setUncertaintyPct(BigDecimal uncertaintyPct) { this.uncertaintyPct = uncertaintyPct; }
  public BigDecimal getLowerValue() { return lowerValue; }
  public void setLowerValue(BigDecimal lowerValue) { this.lowerValue = lowerValue; }
  public BigDecimal getUpperValue() { return upperValue; }
  public void setUpperValue(BigDecimal upperValue) { this.upperValue = upperValue; }
  public MetricStatus getMetricStatus() { return metricStatus; }
  public void setMetricStatus(MetricStatus metricStatus) { this.metricStatus = metricStatus; }
  public String getCalcPathJson() { return calcPathJson; }
  public void setCalcPathJson(String calcPathJson) { this.calcPathJson = calcPathJson; }
  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }
}
