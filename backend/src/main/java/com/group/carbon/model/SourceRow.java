package com.group.carbon.model;

import com.group.carbon.common.Types.RowStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "source_rows")
public class SourceRow {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "batch_id", nullable = false)
  private Long batchId;
  @Column(name = "row_no", nullable = false)
  private int rowNo;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(name = "product_line_id")
  private Long productLineId;
  @Column(nullable = false)
  private String scope;
  @Column(name = "energy_type", nullable = false)
  private String energyType;
  @Column(nullable = false)
  private BigDecimal quantity;
  @Column(nullable = false)
  private String unit;
  /** 计量基准 WET/DRY/STD/OP/LHV；空=未声明 */
  private String basis;
  @Column(name = "voucher_no")
  private String voucherNo;
  @Column(nullable = false)
  private String period;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RowStatus status = RowStatus.ACTIVE;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getBatchId() { return batchId; }
  public void setBatchId(Long batchId) { this.batchId = batchId; }
  public int getRowNo() { return rowNo; }
  public void setRowNo(int rowNo) { this.rowNo = rowNo; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public Long getProductLineId() { return productLineId; }
  public void setProductLineId(Long productLineId) { this.productLineId = productLineId; }
  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public BigDecimal getQuantity() { return quantity; }
  public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
  public String getUnit() { return unit; }
  public void setUnit(String unit) { this.unit = unit; }
  public String getBasis() { return basis; }
  public void setBasis(String basis) { this.basis = basis; }
  public String getVoucherNo() { return voucherNo; }
  public void setVoucherNo(String voucherNo) { this.voucherNo = voucherNo; }
  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }
  public RowStatus getStatus() { return status; }
  public void setStatus(RowStatus status) { this.status = status; }
}
