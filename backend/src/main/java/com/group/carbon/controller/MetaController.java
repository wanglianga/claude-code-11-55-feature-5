package com.group.carbon.controller;

import com.group.carbon.common.EnergyTypes;
import com.group.carbon.repo.PlantRepo;
import com.group.carbon.repo.ProductLineRepo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/meta")
public class MetaController {
  private final PlantRepo plantRepo;
  private final ProductLineRepo lineRepo;

  public MetaController(PlantRepo plantRepo, ProductLineRepo lineRepo) {
    this.plantRepo = plantRepo;
    this.lineRepo = lineRepo;
  }

  @GetMapping("/plants")
  public Object plants() { return plantRepo.findAll(); }

  @GetMapping("/product-lines")
  public Object productLines() { return lineRepo.findAll(); }

  @GetMapping("/energy-types")
  public Object energyTypes() {
    return EnergyTypes.MAP.entrySet().stream().map(e -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("code", e.getKey());
      m.put("scope", e.getValue().scope());
      m.put("unit", e.getValue().unit());
      m.put("label", e.getValue().label());
      m.put("category", e.getValue().category());
      return m;
    }).toList();
  }
}
