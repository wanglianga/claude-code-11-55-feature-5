package com.group.carbon.repo;

import com.group.carbon.model.ConversionConflict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversionConflictRepo extends JpaRepository<ConversionConflict, Long> {
  List<ConversionConflict> findByRunIdOrderByIdDesc(Long runId);
  List<ConversionConflict> findAllByOrderByIdDesc();
  List<ConversionConflict> findBySourceRowIdAndStatus(Long sourceRowId, com.group.carbon.common.Types.ConflictStatus status);
}
