package com.group.carbon.repo;

import com.group.carbon.model.DerivedMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DerivedMetricRepo extends JpaRepository<DerivedMetric, Long> {
  List<DerivedMetric> findByRunIdOrderById(Long runId);
  List<DerivedMetric> findByRunIdAndMetricKind(Long runId, com.group.carbon.common.Types.MetricKind kind);
  List<DerivedMetric> findByResultId(Long resultId);
}
