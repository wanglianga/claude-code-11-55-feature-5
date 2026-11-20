package com.group.carbon.repo;

import com.group.carbon.common.Types.BatchStatus;
import com.group.carbon.model.SourceBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SourceBatchRepo extends JpaRepository<SourceBatch, Long> {
  List<SourceBatch> findAllByOrderByIdDesc();
  List<SourceBatch> findByPeriodOrderByIdDesc(String period);
  Optional<SourceBatch> findByBatchNo(String batchNo);
  boolean existsByReplacesBatchIdAndStatus(Long replacesBatchId, BatchStatus status);
  List<SourceBatch> findByReplacesBatchIdOrderByIdDesc(Long replacesBatchId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM SourceBatch b WHERE b.id = :id")
  Optional<SourceBatch> findByIdForUpdate(@Param("id") Long id);
}
