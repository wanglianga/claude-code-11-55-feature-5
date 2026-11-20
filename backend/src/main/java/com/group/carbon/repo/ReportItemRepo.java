package com.group.carbon.repo;

import com.group.carbon.model.ReportItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportItemRepo extends JpaRepository<ReportItem, Long> {
  List<ReportItem> findByReportVersionIdOrderById(Long reportVersionId);
}
