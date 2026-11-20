package com.group.carbon.repo;

import com.group.carbon.model.Factor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FactorRepo extends JpaRepository<Factor, Long> {
  Optional<Factor> findByCode(String code);
}
