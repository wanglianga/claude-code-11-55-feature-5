package com.group.carbon.repo;

import com.group.carbon.model.RunFactorBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunFactorBindingRepo extends JpaRepository<RunFactorBinding, Long> {
  List<RunFactorBinding> findByRunId(Long runId);
  List<RunFactorBinding> findByFactorVersionId(Long factorVersionId);
}
