package com.group.carbon.model;

import jakarta.persistence.*;

@Entity
@Table(name = "factors")
public class Factor {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, unique = true)
  private String code;
  @Column(nullable = false)
  private String name;
  @Column(nullable = false)
  private String scope;
  @Column(name = "energy_type", nullable = false)
  private String energyType;
  @Column(nullable = false)
  private String region;
  @Column(nullable = false)
  private String unit;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public String getRegion() { return region; }
  public void setRegion(String region) { this.region = region; }
  public String getUnit() { return unit; }
  public void setUnit(String unit) { this.unit = unit; }
}
