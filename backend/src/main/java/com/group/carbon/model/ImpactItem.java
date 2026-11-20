package com.group.carbon.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "impact_items")
public class ImpactItem {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "impact_id", nullable = false)
  private Long impactId;
  @Column(name = "plant_id")
  private Long plantId;
  @Column(name = "product_line_id")
  private Long productLineId;
  @Column
  private String scope;
  @Column(nullable = false)
  private String metric;
  @Column(name = "old_value")
  private BigDecimal oldValue;
  @Column(name = "new_value")
  private BigDecimal newValue;
  @Column
  private BigDecimal diff;
  @Column(name = "affected_report_id")
  private Long affectedReportId;
  @Column(name = "affected_report_version_id")
  private Long affectedReportVersionId;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getImpactId() { return impactId; }
  public void setImpactId(Long impactId) { this.impactId = impactId; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public Long getProductLineId() { return productLineId; }
  public void setProductLineId(Long productLineId) { this.productLineId = productLineId; }
  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope; }
  public String getMetric() { return metric; }
  public void setMetric(String metric) { this.metric = metric; }
  public BigDecimal getOldValue() { return oldValue; }
  public void setOldValue(BigDecimal oldValue) { this.oldValue = oldValue; }
  public BigDecimal getNewValue() { return newValue; }
  public void setNewValue(BigDecimal newValue) { this.newValue = newValue; }
  public BigDecimal getDiff() { return diff; }
  public void setDiff(BigDecimal diff) { this.diff = diff; }
  public Long getAffectedReportId() { return affectedReportId; }
  public void setAffectedReportId(Long affectedReportId) { this.affectedReportId = affectedReportId; }
  public Long getAffectedReportVersionId() { return affectedReportVersionId; }
  public void setAffectedReportVersionId(Long affectedReportVersionId) { this.affectedReportVersionId = affectedReportVersionId; }
}
