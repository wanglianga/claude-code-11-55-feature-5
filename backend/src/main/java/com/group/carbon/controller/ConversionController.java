package com.group.carbon.controller;

import com.group.carbon.common.AuthContext;
import com.group.carbon.common.Types.Role;
import com.group.carbon.dto.Requests.CreateConversionBaseReq;
import com.group.carbon.dto.Requests.CreateEvidenceReq;
import com.group.carbon.service.ConversionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 测量换算与派生指标接口：
 * 证据/依据/有向换算边维护、活动量换算证明（四类状态）、冲突、派生指标与披露单元格追溯。
 */
@RestController
@RequestMapping("/api/conversion")
public class ConversionController {
  private final ConversionService conversionService;

  public ConversionController(ConversionService conversionService) {
    this.conversionService = conversionService;
  }

  // ---------- 校准/化验证据 ----------

  @GetMapping("/evidence")
  public Object listEvidence() { return conversionService.listEvidence(); }

  @PostMapping("/evidence")
  public Object createEvidence(@Valid @RequestBody CreateEvidenceReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return conversionService.createEvidence(AuthContext.require().username(), req);
  }

  // ---------- 换算依据与有向边 ----------

  @GetMapping("/bases")
  public Object listBases() { return conversionService.listBases(); }

  @PostMapping("/bases")
  public Object createBase(@Valid @RequestBody CreateConversionBaseReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return conversionService.createBase(AuthContext.require().username(), req);
  }

  @PostMapping("/bases/{id}/approve")
  public Object approveBase(@PathVariable Long id) {
    AuthContext.requireRole(Role.AUDITOR, Role.ADMIN);
    return conversionService.approveBase(id, AuthContext.require().username());
  }

  // ---------- 活动量换算证明 ----------

  @GetMapping("/runs/{runId}/conversions")
  public Object conversions(@PathVariable Long runId) {
    return conversionService.conversionsOfRun(runId);
  }

  @GetMapping("/runs/{runId}/summary")
  public Map<String, Object> summary(@PathVariable Long runId) {
    return conversionService.runSummary(runId);
  }

  @GetMapping("/runs/{runId}/conflicts")
  public Object runConflicts(@PathVariable Long runId) {
    return conversionService.conflictsOfRun(runId);
  }

  @GetMapping("/runs/{runId}/metrics")
  public Object metrics(@PathVariable Long runId) {
    return conversionService.metricsOfRun(runId);
  }

  @GetMapping("/conversions/{id}")
  public Map<String, Object> conversionDetail(@PathVariable Long id) {
    return conversionService.conversionDetail(id);
  }

  @GetMapping("/conflicts")
  public Object allConflicts() { return conversionService.allConflicts(); }

  // ---------- 披露单元格追溯 ----------

  @GetMapping("/versions/{versionId}/traces")
  public Object traces(@PathVariable Long versionId) {
    return conversionService.tracesOfVersion(versionId);
  }
}
