package com.group.carbon.model;

import com.group.carbon.common.Types.EvidenceType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 校准/化验证据：换算依据引用的原始凭证（证书、化验报告、密度修订单、人工桥接说明）。 */
@Entity
@Table(name = "measurement_evidence")
public class MeasurementEvidence {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "evidence_no", nullable = false)
  private String evidenceNo;
  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_type", nullable = false)
  private EvidenceType evidenceType;
  @Column(nullable = false)
  private String title;
  @Column(name = "source_ref")
  private String sourceRef;
  private String issuer;
  @Column(name = "issued_date", nullable = false)
  private LocalDate issuedDate;
  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;
  @Column(name = "effective_to")
  private LocalDate effectiveTo;
  @Column(name = "plant_id")
  private Long plantId;
  @Column(name = "energy_type")
  private String energyType;
  @Column(name = "measured_prop", nullable = false)
  private String measuredProp;
  @Column(name = "nominal_value", nullable = false)
  private BigDecimal nominalValue;
  @Column(nullable = false)
  private String unit;
  @Column(name = "sig_figs")
  private Integer sigFigs;
  @Column(name = "uncertainty_pct")
  private BigDecimal uncertaintyPct;
  private String note;
  @Column(name = "created_by", nullable = false)
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getEvidenceNo() { return evidenceNo; }
  public void setEvidenceNo(String evidenceNo) { this.evidenceNo = evidenceNo; }
  public EvidenceType getEvidenceType() { return evidenceType; }
  public void setEvidenceType(EvidenceType evidenceType) { this.evidenceType = evidenceType; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getSourceRef() { return sourceRef; }
  public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
  public String getIssuer() { return issuer; }
  public void setIssuer(String issuer) { this.issuer = issuer; }
  public LocalDate getIssuedDate() { return issuedDate; }
  public void setIssuedDate(LocalDate issuedDate) { this.issuedDate = issuedDate; }
  public LocalDate getEffectiveFrom() { return effectiveFrom; }
  public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
  public LocalDate getEffectiveTo() { return effectiveTo; }
  public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public String getMeasuredProp() { return measuredProp; }
  public void setMeasuredProp(String measuredProp) { this.measuredProp = measuredProp; }
  public BigDecimal getNominalValue() { return nominalValue; }
  public void setNominalValue(BigDecimal nominalValue) { this.nominalValue = nominalValue; }
  public String getUnit() { return unit; }
  public void setUnit(String unit) { this.unit = unit; }
  public Integer getSigFigs() { return sigFigs; }
  public void setSigFigs(Integer sigFigs) { this.sigFigs = sigFigs; }
  public BigDecimal getUncertaintyPct() { return uncertaintyPct; }
  public void setUncertaintyPct(BigDecimal uncertaintyPct) { this.uncertaintyPct = uncertaintyPct; }
  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
