package com.group.carbon.repo;

import com.group.carbon.model.Restatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestatementRepo extends JpaRepository<Restatement, Long> {
  List<Restatement> findByReportIdOrderByIdDesc(Long reportId);
  List<Restatement> findByImpactId(Long impactId);
  List<Restatement> findByNewVersionId(Long newVersionId);
}
