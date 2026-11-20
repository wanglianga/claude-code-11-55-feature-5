package com.group.carbon.controller;

import com.group.carbon.common.AuthContext;
import com.group.carbon.common.Types.Role;
import com.group.carbon.dto.Requests.ReasonReq;
import com.group.carbon.service.ImpactService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/impacts")
public class ImpactController {
  private final ImpactService impactService;

  public ImpactController(ImpactService impactService) {
    this.impactService = impactService;
  }

  @GetMapping
  public Object list() { return impactService.list(); }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable Long id) {
    return impactService.detail(id);
  }

  @PostMapping("/restatements/{id}/accept")
  public Map<String, Object> accept(@PathVariable Long id) {
    AuthContext.requireRole(Role.FINANCE, Role.ADMIN);
    return impactService.acceptRestatement(id, AuthContext.require().username());
  }

  @PostMapping("/restatements/{id}/reject")
  public Object reject(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
    AuthContext.requireRole(Role.FINANCE, Role.ADMIN);
    return impactService.rejectRestatement(id, AuthContext.require().username(),
        req != null ? req.reason() : null);
  }
}
