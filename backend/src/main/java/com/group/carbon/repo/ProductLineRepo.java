package com.group.carbon.repo;

import com.group.carbon.model.ProductLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductLineRepo extends JpaRepository<ProductLine, Long> {
  Optional<ProductLine> findByCode(String code);
}
