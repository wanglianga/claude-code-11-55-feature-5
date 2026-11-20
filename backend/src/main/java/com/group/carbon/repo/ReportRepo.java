package com.group.carbon.repo;

import com.group.carbon.model.Report;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportRepo extends JpaRepository<Report, Long> {
  List<Report> findAllByOrderByIdDesc();
  List<Report> findByPeriod(String period);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM Report r WHERE r.id = :id")
  Optional<Report> findByIdForUpdate(@Param("id") Long id);
}
