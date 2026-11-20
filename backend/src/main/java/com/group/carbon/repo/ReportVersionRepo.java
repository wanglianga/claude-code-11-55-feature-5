package com.group.carbon.repo;

import com.group.carbon.common.Types.VersionStatus;
import com.group.carbon.model.ReportVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportVersionRepo extends JpaRepository<ReportVersion, Long> {
  List<ReportVersion> findByReportIdOrderByVersionNoDesc(Long reportId);
  Optional<ReportVersion> findFirstByReportIdAndStatusOrderByVersionNoDesc(Long reportId, VersionStatus status);
  List<ReportVersion> findByStatusOrderByPublishedAtDesc(VersionStatus status);

  /** 某期间当前已发布的报告版本（每个报告取最新一个已发布版本） */
  @Query("SELECT v FROM ReportVersion v, Report r WHERE v.reportId = r.id AND r.period = :period " +
         "AND v.status = 'PUBLISHED' ORDER BY v.versionNo DESC")
  List<ReportVersion> findPublishedByPeriod(@Param("period") String period);
}
