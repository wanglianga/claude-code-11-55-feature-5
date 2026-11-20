package com.group.carbon.repo;

import com.group.carbon.model.MeasurementEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeasurementEvidenceRepo extends JpaRepository<MeasurementEvidence, Long> {
  Optional<MeasurementEvidence> findByEvidenceNo(String evidenceNo);
  List<MeasurementEvidence> findAllByOrderByIdDesc();
  List<MeasurementEvidence> findByMeasuredPropOrderByIdDesc(String measuredProp);
}
