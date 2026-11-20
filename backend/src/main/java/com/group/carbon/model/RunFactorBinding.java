package com.group.carbon.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 运行启动时锁定的因子清单快照：运行期间因子被替换也不影响本运行。 */
@Entity
@Table(name = "run_factor_bindings")
public class RunFactorBinding {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "run_id", nullable = false)
  private Long runId;
  @Column(name = "factor_version_id", nullable = false)
  private Long factorVersionId;
  @Column(name = "factor_code", nullable = false)
  private String factorCode;
  @Column(nullable = false)
  private String region;
  @Column(name = "energy_type", nullable = false)
  private String energyType;
  @Column(nullable = false)
  private BigDecimal value;
  @Column(name = "valid_from")
  private LocalDate validFrom;
  @Column(name = "valid_to")
  private LocalDate validTo;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getRunId() { return runId; }
  public void setRunId(Long runId) { this.runId = runId; }
  public Long getFactorVersionId() { return factorVersionId; }
  public void setFactorVersionId(Long factorVersionId) { this.factorVersionId = factorVersionId; }
  public String getFactorCode() { return factorCode; }
  public void setFactorCode(String factorCode) { this.factorCode = factorCode; }
  public String getRegion() { return region; }
  public void setRegion(String region) { this.region = region; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public BigDecimal getValue() { return value; }
  public void setValue(BigDecimal value) { this.value = value; }
  public LocalDate getValidFrom() { return validFrom; }
  public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
  public LocalDate getValidTo() { return validTo; }
  public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
}
