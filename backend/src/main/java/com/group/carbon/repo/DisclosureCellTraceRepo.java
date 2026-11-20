package com.group.carbon.repo;

import com.group.carbon.model.DisclosureCellTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisclosureCellTraceRepo extends JpaRepository<DisclosureCellTrace, Long> {
  List<DisclosureCellTrace> findByReportVersionIdOrderById(Long reportVersionId);
  List<DisclosureCellTrace> findByReportItemId(Long reportItemId);
}
