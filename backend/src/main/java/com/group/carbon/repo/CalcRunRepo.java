package com.group.carbon.repo;

import com.group.carbon.common.Types.RunPurpose;
import com.group.carbon.common.Types.RunStatus;
import com.group.carbon.model.CalcRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalcRunRepo extends JpaRepository<CalcRun, Long> {
  List<CalcRun> findAllByOrderByIdDesc();
  List<CalcRun> findByPeriodOrderByIdDesc(String period);
  Optional<CalcRun> findFirstByPeriodAndPurposeAndStatusOrderByIdDesc(String period, RunPurpose purpose, RunStatus status);
}
