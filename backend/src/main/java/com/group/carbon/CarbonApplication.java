package com.group.carbon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class CarbonApplication {
  public static void main(String[] args) {
    SpringApplication.run(CarbonApplication.class, args);
  }
}
