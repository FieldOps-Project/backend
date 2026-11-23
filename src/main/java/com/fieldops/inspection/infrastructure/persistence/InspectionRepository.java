package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.Inspection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface InspectionRepository extends JpaRepository<Inspection, UUID>, JpaSpecificationExecutor<Inspection> {

    @EntityGraph(attributePaths = "itemSnapshots")
    Optional<Inspection> findWithSnapshotsById(UUID id);

    @EntityGraph(attributePaths = "itemSnapshots")
    Optional<Inspection> findWithSnapshotsByIdAndTechnicianId(UUID id, UUID technicianId);

    Optional<Inspection> findByIdAndSupervisorId(UUID id, UUID supervisorId);

    List<Inspection> findAllByTechnicianId(UUID technicianId);

    List<Inspection> findAllBySupervisorId(UUID supervisorId);
}
