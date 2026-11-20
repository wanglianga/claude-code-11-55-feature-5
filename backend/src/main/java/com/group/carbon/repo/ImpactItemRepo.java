package com.group.carbon.repo;

import com.group.carbon.model.ImpactItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImpactItemRepo extends JpaRepository<ImpactItem, Long> {
  List<ImpactItem> findByImpactIdOrderById(Long impactId);
}
