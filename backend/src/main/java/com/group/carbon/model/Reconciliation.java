package com.group.carbon.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 勾稽记录：产品线合计 与 工厂明细汇总 的对照；差异不为零则报告保持待核。 */
@Entity
@Table(name = "reconciliations")
public class Reconciliation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "report_version_id", nullable = false)
  private Long reportVersionId;
  @Column(name = "product_line_id")
  private Long productLineId;
  @Column(nullable = false)
  private String label;
  @Column(name = "plant_detail_sum", nullable = false)
  private BigDecimal plantDetailSum;
  @Column(name = "product_line_total", nullable = false)
  private BigDecimal productLineTotal;
  @Column(nullable = false)
  private BigDecimal diff;
  @Column(nullable = false)
  private String status;   // OK / MISMATCH / PENDING_FACTOR

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getReportVersionId() { return reportVersionId; }
  public void setReportVersionId(Long reportVersionId) { this.reportVersionId = reportVersionId; }
  public Long getProductLineId() { return productLineId; }
  public void setProductLineId(Long productLineId) { this.productLineId = productLineId; }
  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = label; }
  public BigDecimal getPlantDetailSum() { return plantDetailSum; }
  public void setPlantDetailSum(BigDecimal plantDetailSum) { this.plantDetailSum = plantDetailSum; }
  public BigDecimal getProductLineTotal() { return productLineTotal; }
  public void setProductLineTotal(BigDecimal productLineTotal) { this.productLineTotal = productLineTotal; }
  public BigDecimal getDiff() { return diff; }
  public void setDiff(BigDecimal diff) { this.diff = diff; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
