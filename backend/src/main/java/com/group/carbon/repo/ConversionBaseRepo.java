package com.group.carbon.repo;

import com.group.carbon.common.Types.ConversionBaseStatus;
import com.group.carbon.model.ConversionBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversionBaseRepo extends JpaRepository<ConversionBase, Long> {
  Optional<ConversionBase> findByCode(String code);
  List<ConversionBase> findAllByOrderByIdDesc();
  List<ConversionBase> findByEnergyTypeAndStatusOrderById(String energyType, ConversionBaseStatus status);
  List<ConversionBase> findByStatusOrderById(ConversionBaseStatus status);
}
