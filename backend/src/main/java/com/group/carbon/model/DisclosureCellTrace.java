package com.group.carbon.model;

import com.group.carbon.common.Types.TraceStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** 披露单元格追溯：披露表能量/排放/强度单元格与可复算换算路径、不确定范围绑定。 */
@Entity
@Table(name = "disclosure_cell_traces")
public class DisclosureCellTrace {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "report_version_id", nullable = false)
  private Long reportVersionId;
  @Column(name = "report_item_id")
  private Long reportItemId;
  @Column(name = "cell_type", nullable = false)
  private String cellType;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(name = "product_line_id")
  private Long productLineId;
  @Column(nullable = false)
  private String scope;
  @Column(name = "energy_type", nullable = false)
  private String energyType;
  @Column(name = "displayed_value")
  private BigDecimal displayedValue;
  @Column(name = "displayed_unit")
  private String displayedUnit;
  @Column(name = "display_decimals", nullable = false)
  private int displayDecimals;
  @Column(name = "lower_value")
  private BigDecimal lowerValue;
  @Column(name = "upper_value")
  private BigDecimal upperValue;
  @Enumerated(EnumType.STRING)
  @Column(name = "trace_status", nullable = false)
  private TraceStatus traceStatus;
  @Column(name = "trace_path_json")
  private String tracePathJson;
  @Column(name = "evidence_summary")
  private String evidenceSummary;
  @Column(name = "frozen_note")
  private String frozenNote;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getReportVersionId() { return reportVersionId; }
  public void setReportVersionId(Long reportVersionId) { this.reportVersionId = reportVersionId; }
  public Long getReportItemId() { return reportItemId; }
  public void setReportItemId(Long reportItemId) { this.reportItemId = reportItemId; }
  public String getCellType() { return cellType; }
  public void setCellType(String cellType) { this.cellType = cellType; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public Long getProductLineId() { return productLineId; }
  public void setProductLineId(Long productLineId) { this.productLineId = productLineId; }
  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public BigDecimal getDisplayedValue() { return displayedValue; }
  public void setDisplayedValue(BigDecimal displayedValue) { this.displayedValue = displayedValue; }
  public String getDisplayedUnit() { return displayedUnit; }
  public void setDisplayedUnit(String displayedUnit) { this.displayedUnit = displayedUnit; }
  public int getDisplayDecimals() { return displayDecimals; }
  public void setDisplayDecimals(int displayDecimals) { this.displayDecimals = displayDecimals; }
  public BigDecimal getLowerValue() { return lowerValue; }
  public void setLowerValue(BigDecimal lowerValue) { this.lowerValue = lowerValue; }
  public BigDecimal getUpperValue() { return upperValue; }
  public void setUpperValue(BigDecimal upperValue) { this.upperValue = upperValue; }
  public TraceStatus getTraceStatus() { return traceStatus; }
  public void setTraceStatus(TraceStatus traceStatus) { this.traceStatus = traceStatus; }
  public String getTracePathJson() { return tracePathJson; }
  public void setTracePathJson(String tracePathJson) { this.tracePathJson = tracePathJson; }
  public String getEvidenceSummary() { return evidenceSummary; }
  public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }
  public String getFrozenNote() { return frozenNote; }
  public void setFrozenNote(String frozenNote) { this.frozenNote = frozenNote; }
}
