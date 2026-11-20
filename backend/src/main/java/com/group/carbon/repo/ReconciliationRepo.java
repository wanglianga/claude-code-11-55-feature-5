package com.group.carbon.repo;

import com.group.carbon.model.Reconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationRepo extends JpaRepository<Reconciliation, Long> {
  List<Reconciliation> findByReportVersionIdOrderById(Long reportVersionId);
}
