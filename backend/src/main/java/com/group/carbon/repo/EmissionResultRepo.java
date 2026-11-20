package com.group.carbon.repo;

import com.group.carbon.model.EmissionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmissionResultRepo extends JpaRepository<EmissionResult, Long> {
  List<EmissionResult> findByRunIdOrderById(Long runId);
}
