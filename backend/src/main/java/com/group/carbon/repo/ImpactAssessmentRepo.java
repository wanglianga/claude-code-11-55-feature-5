package com.group.carbon.repo;

import com.group.carbon.model.ImpactAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImpactAssessmentRepo extends JpaRepository<ImpactAssessment, Long> {
  List<ImpactAssessment> findAllByOrderByIdDesc();
}
