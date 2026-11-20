package com.group.carbon.repo;

import com.group.carbon.common.Types.ConversionStatus;
import com.group.carbon.model.ActivityConversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityConversionRepo extends JpaRepository<ActivityConversion, Long> {
  List<ActivityConversion> findByRunIdOrderById(Long runId);
  List<ActivityConversion> findBySourceRowId(Long sourceRowId);
  List<ActivityConversion> findByRunIdAndConvStatus(Long runId, ConversionStatus status);
  List<ActivityConversion> findByRunIdAndConflictFlagTrue(Long runId);
}
