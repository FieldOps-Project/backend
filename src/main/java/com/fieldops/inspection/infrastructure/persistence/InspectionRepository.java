package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.Inspection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

    @EntityGraph(attributePaths = "itemSnapshots")
    Optional<Inspection> findWithSnapshotsById(UUID id);
}
