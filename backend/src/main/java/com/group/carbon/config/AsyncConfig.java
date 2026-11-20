package com.group.carbon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {
  /** 核算运行异步执行器：官方运行启动后延迟执行，便于演示运行中因子替换仍绑定启动清单。 */
  @Bean("calcRunTaskExecutor")
  public Executor calcRunTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(20);
    executor.setThreadNamePrefix("calc-run-");
    executor.initialize();
    return executor;
  }
}
