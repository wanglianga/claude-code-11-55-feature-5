package com.group.carbon.repo;

import com.group.carbon.model.ConversionEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversionEdgeRepo extends JpaRepository<ConversionEdge, Long> {
  List<ConversionEdge> findByStatusOrderById(String status);
  List<ConversionEdge> findByBaseId(Long baseId);
  List<ConversionEdge> findByFromDimensionAndStatusOrderById(String fromDimension, String status);
}
