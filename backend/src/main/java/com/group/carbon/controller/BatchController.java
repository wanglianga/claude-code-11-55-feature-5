package com.group.carbon.controller;

import com.group.carbon.common.AuthContext;
import com.group.carbon.common.Types.Role;
import com.group.carbon.dto.Requests.CreateBatchReq;
import com.group.carbon.dto.Requests.ReasonReq;
import com.group.carbon.dto.Requests.VoidReq;
import com.group.carbon.service.BatchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/batches")
public class BatchController {
  private final BatchService batchService;

  public BatchController(BatchService batchService) {
    this.batchService = batchService;
  }

  @GetMapping
  public Object list(@RequestParam(required = false) String period,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String category) {
    return batchService.list(period, status, category);
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable Long id) {
    return batchService.detail(id);
  }

  @PostMapping
  public Object create(@Valid @RequestBody CreateBatchReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return batchService.create(AuthContext.require().username(), req);
  }

  @PostMapping("/{id}/verify")
  public Object verify(@PathVariable Long id) {
    AuthContext.requireRole(Role.AUDITOR, Role.ADMIN);
    return batchService.verify(id, AuthContext.require().username());
  }

  @PostMapping("/{id}/reject")
  public Object reject(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
    AuthContext.requireRole(Role.AUDITOR, Role.ADMIN);
    return batchService.reject(id, AuthContext.require().username(),
        req != null ? req.reason() : null);
  }

  @PostMapping("/{id}/revise")
  public Object revise(@PathVariable Long id, @Valid @RequestBody CreateBatchReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return batchService.revise(id, AuthContext.require().username(), req);
  }

  @PostMapping("/{id}/void")
  public Object voidBatch(@PathVariable Long id, @Valid @RequestBody VoidReq req) {
    AuthContext.requireRole(Role.AUDITOR, Role.ADMIN);
    return batchService.voidBatch(id, AuthContext.require().username(), req.reason());
  }
}
