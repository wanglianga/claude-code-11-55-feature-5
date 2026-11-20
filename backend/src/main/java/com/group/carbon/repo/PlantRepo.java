package com.group.carbon.repo;

import com.group.carbon.model.Plant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlantRepo extends JpaRepository<Plant, Long> {
  Optional<Plant> findByCode(String code);
}
