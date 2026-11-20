package com.group.carbon.controller;

import com.group.carbon.common.AuthContext;
import com.group.carbon.common.Types.Role;
import com.group.carbon.dto.Requests.CreateRunReq;
import com.group.carbon.model.CalcRun;
import com.group.carbon.service.RunExecutor;
import com.group.carbon.service.RunService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/runs")
public class RunController {
  private final RunService runService;
  private final RunExecutor runExecutor;
  private final long asyncDelayMs;

  public RunController(RunService runService, RunExecutor runExecutor,
                       @Value("${app.run-async-delay-ms:2000}") long asyncDelayMs) {
    this.runService = runService;
    this.runExecutor = runExecutor;
    this.asyncDelayMs = asyncDelayMs;
  }

  @GetMapping
  public Object list() { return runService.listRuns(); }

  @PostMapping
  public Object create(@Valid @RequestBody CreateRunReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    CalcRun run = runService.createOfficialRun(req.period(), AuthContext.require().username());
    runExecutor.executeAsync(run.getId(), asyncDelayMs);
    return run;
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable Long id) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("run", runService.getRun(id));
    out.put("bindings", runService.bindingsOf(id));
    out.put("results", runService.resultsOf(id));
    return out;
  }

  @GetMapping("/{id}/results")
  public Object results(@PathVariable Long id) {
    return runService.resultsOf(id);
  }
}

@RestController
@RequestMapping("/api/results")
class ResultController {
  private final RunService runService;

  ResultController(RunService runService) {
    this.runService = runService;
  }

  @GetMapping("/{resultId}/lineage")
  public Map<String, Object> lineage(@PathVariable Long resultId) {
    return runService.lineageOf(resultId);
  }
}
