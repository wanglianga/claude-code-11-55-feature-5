package com.group.carbon.service;

import com.group.carbon.common.BizException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 官方运行的异步执行器：启动后延迟执行，期间因子替换不影响已绑定的清单。 */
@Component
public class RunExecutor {
  private final RunService runService;

  public RunExecutor(RunService runService) {
    this.runService = runService;
  }

  @Async("calcRunTaskExecutor")
  public void executeAsync(Long runId, long delayMs) {
    if (delayMs > 0) {
      try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
    runService.execute(runId);
  }
}
