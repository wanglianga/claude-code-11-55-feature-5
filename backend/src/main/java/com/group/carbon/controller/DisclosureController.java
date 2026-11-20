package com.group.carbon.controller;

import com.group.carbon.common.AuthContext;
import com.group.carbon.common.Types.Role;
import com.group.carbon.dto.Requests.CreateReportReq;
import com.group.carbon.dto.Requests.FreezeReq;
import com.group.carbon.service.DisclosureService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class DisclosureController {
  private final DisclosureService disclosureService;

  public DisclosureController(DisclosureService disclosureService) {
    this.disclosureService = disclosureService;
  }

  @GetMapping
  public Object list() { return disclosureService.listReports(); }

  @PostMapping
  public Object create(@Valid @RequestBody CreateReportReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return disclosureService.createReport(AuthContext.require().username(),
        req.title(), req.period(), req.runId());
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable Long id) {
    return disclosureService.detail(id);
  }

  @PostMapping("/{id}/freeze")
  public Object freeze(@PathVariable Long id, @Valid @RequestBody FreezeReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return disclosureService.freeze(id, req.runId(), AuthContext.require().username(), null, req.note());
  }

  @PostMapping("/{id}/approve")
  public Object approve(@PathVariable Long id) {
    AuthContext.requireRole(Role.FINANCE, Role.ADMIN);
    return disclosureService.approve(id, AuthContext.require().username());
  }

  @PostMapping("/{id}/publish")
  public Object publish(@PathVariable Long id) {
    AuthContext.requireRole(Role.FINANCE, Role.ADMIN);
    return disclosureService.publish(id, AuthContext.require().username());
  }

  @GetMapping("/versions/{versionId}")
  public Map<String, Object> versionDetail(@PathVariable Long versionId) {
    return disclosureService.versionDetail(versionId);
  }
}
