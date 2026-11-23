package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.InspectionResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionResponseRepository extends JpaRepository<InspectionResponse, UUID> {

    List<InspectionResponse> findAllByInspectionIdOrderByAnsweredAtServerAsc(UUID inspectionId);

    Optional<InspectionResponse> findByIdAndInspectionId(UUID id, UUID inspectionId);

    Optional<InspectionResponse> findByInspectionIdAndInspectionItemId(UUID inspectionId, UUID inspectionItemId);
}
