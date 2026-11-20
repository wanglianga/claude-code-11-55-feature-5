package com.group.carbon.controller;

import com.group.carbon.common.AuthContext;
import com.group.carbon.common.Types.Role;
import com.group.carbon.dto.Requests.CreateFactorReq;
import com.group.carbon.dto.Requests.CreateVersionReq;
import com.group.carbon.service.FactorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/factors")
public class FactorController {
  private final FactorService factorService;

  public FactorController(FactorService factorService) {
    this.factorService = factorService;
  }

  @GetMapping
  public Object list() { return factorService.listFactors(); }

  @PostMapping
  public Object create(@Valid @RequestBody CreateFactorReq req) {
    AuthContext.requireRole(Role.ADMIN);
    return factorService.createFactor(AuthContext.require().username(), req);
  }

  @PostMapping("/{id}/versions")
  public Object createVersion(@PathVariable Long id, @Valid @RequestBody CreateVersionReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return factorService.createVersion(id, AuthContext.require().username(), req);
  }

  @PostMapping("/versions/{versionId}/approve")
  public Object approve(@PathVariable Long versionId) {
    AuthContext.requireRole(Role.AUDITOR, Role.ADMIN);
    return factorService.approve(versionId, AuthContext.require().username());
  }

  @PostMapping("/versions/{versionId}/retire")
  public Object retire(@PathVariable Long versionId) {
    AuthContext.requireRole(Role.AUDITOR, Role.ADMIN);
    return factorService.retire(versionId, AuthContext.require().username());
  }

  @GetMapping("/versions/{versionId}/usage")
  public Map<String, Object> usage(@PathVariable Long versionId) {
    return factorService.usageOf(versionId);
  }
}
