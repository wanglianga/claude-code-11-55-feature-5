package com.group.carbon.model;

import com.group.carbon.common.Types.ResultStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "report_items")
public class ReportItem {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "report_version_id", nullable = false)
  private Long reportVersionId;
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
  @Column(name = "emission_tco2e")
  private BigDecimal emissionTco2e;
  @Enumerated(EnumType.STRING)
  @Column(name = "item_status", nullable = false)
  private ResultStatus itemStatus;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getReportVersionId() { return reportVersionId; }
  public void setReportVersionId(Long reportVersionId) { this.reportVersionId = reportVersionId; }
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
  public BigDecimal getEmissionTco2e() { return emissionTco2e; }
  public void setEmissionTco2e(BigDecimal emissionTco2e) { this.emissionTco2e = emissionTco2e; }
  public ResultStatus getItemStatus() { return itemStatus; }
  public void setItemStatus(ResultStatus itemStatus) { this.itemStatus = itemStatus; }
}
