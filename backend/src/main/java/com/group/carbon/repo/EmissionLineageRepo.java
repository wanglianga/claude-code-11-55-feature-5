package com.group.carbon.repo;

import com.group.carbon.model.EmissionLineage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EmissionLineageRepo extends JpaRepository<EmissionLineage, Long> {
  List<EmissionLineage> findByResultId(Long resultId);
  List<EmissionLineage> findBySourceRowIdIn(Collection<Long> sourceRowIds);
}
