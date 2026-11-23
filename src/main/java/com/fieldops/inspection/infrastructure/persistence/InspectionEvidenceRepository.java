package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.InspectionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionEvidenceRepository extends JpaRepository<InspectionEvidence, UUID> {

    List<InspectionEvidence> findAllByInspectionIdAndDeletedAtIsNullOrderByServerReceivedAtAsc(UUID inspectionId);

    Optional<InspectionEvidence> findByIdAndInspectionId(UUID id, UUID inspectionId);

    boolean existsByResponseIdAndDeletedAtIsNull(UUID responseId);
}
