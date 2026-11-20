package com.group.carbon.repo;

import com.group.carbon.common.Types.FactorStatus;
import com.group.carbon.model.FactorVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FactorVersionRepo extends JpaRepository<FactorVersion, Long> {
  List<FactorVersion> findByStatus(FactorStatus status);
  List<FactorVersion> findByFactorIdOrderByVersionNoDesc(Long factorId);
  List<FactorVersion> findByFactorIdAndStatus(Long factorId, FactorStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT v FROM FactorVersion v WHERE v.id = :id")
  Optional<FactorVersion> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT v FROM FactorVersion v WHERE v.factorId = :factorId AND v.status = 'APPROVED'")
  List<FactorVersion> findApprovedForUpdate(@Param("factorId") Long factorId);
}
