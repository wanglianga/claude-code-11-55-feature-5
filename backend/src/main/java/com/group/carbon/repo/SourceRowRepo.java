package com.group.carbon.repo;

import com.group.carbon.common.Types.RowStatus;
import com.group.carbon.model.SourceRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SourceRowRepo extends JpaRepository<SourceRow, Long> {
  List<SourceRow> findByBatchIdOrderByRowNo(Long batchId);
  List<SourceRow> findByVoucherNo(String voucherNo);

  /** 某期间内、属于已核验批次且处于生效状态的活动数据行 */
  @Query("SELECT r FROM SourceRow r, SourceBatch b " +
         "WHERE r.batchId = b.id AND b.status = 'VERIFIED' " +
         "AND r.period = :period AND r.status = :status")
  List<SourceRow> findActiveVerifiedByPeriod(@Param("period") String period,
                                             @Param("status") RowStatus status);
}
