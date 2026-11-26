package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.dashboard.infrastructure.persistence.InspectionStatusCountProjection;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface InspectionRepository extends JpaRepository<Inspection, UUID>, JpaSpecificationExecutor<Inspection> {

    @EntityGraph(attributePaths = "itemSnapshots")
    Optional<Inspection> findWithSnapshotsById(UUID id);

    @EntityGraph(attributePaths = "itemSnapshots")
    Optional<Inspection> findWithSnapshotsByIdAndTechnicianId(UUID id, UUID technicianId);

    Optional<Inspection> findByIdAndSupervisorId(UUID id, UUID supervisorId);

    boolean existsByIdAndTechnicianId(UUID id, UUID technicianId);

    List<Inspection> findAllByTechnicianId(UUID technicianId);

    List<Inspection> findAllBySupervisorId(UUID supervisorId);

    @Query("""
            select i.status as status, count(i.id) as count
            from Inspection i
            where (:periodStart is null or i.scheduledFor >= :periodStart)
              and (:periodEnd is null or i.scheduledFor <= :periodEnd)
              and (:supervisorId is null or i.supervisorId = :supervisorId)
            group by i.status
            order by i.status
            """)
    List<InspectionStatusCountProjection> countByStatusForDashboard(
            @Param("periodStart") java.time.Instant periodStart,
            @Param("periodEnd") java.time.Instant periodEnd,
            @Param("supervisorId") UUID supervisorId
    );

    @Query("""
            select count(i.id)
            from Inspection i
            where (:periodStart is null or i.scheduledFor >= :periodStart)
              and (:periodEnd is null or i.scheduledFor <= :periodEnd)
              and (:supervisorId is null or i.supervisorId = :supervisorId)
            """)
    long countForDashboard(
            @Param("periodStart") java.time.Instant periodStart,
            @Param("periodEnd") java.time.Instant periodEnd,
            @Param("supervisorId") UUID supervisorId
    );

    @Query("""
            select count(i.id)
            from Inspection i
            where (:periodStart is null or i.scheduledFor >= :periodStart)
              and (:periodEnd is null or i.scheduledFor <= :periodEnd)
              and (:supervisorId is null or i.supervisorId = :supervisorId)
              and i.scheduledFor < :now
              and i.status in :overdueStatuses
            """)
    long countOverdueForDashboard(
            @Param("periodStart") java.time.Instant periodStart,
            @Param("periodEnd") java.time.Instant periodEnd,
            @Param("supervisorId") UUID supervisorId,
            @Param("now") java.time.Instant now,
            @Param("overdueStatuses") java.util.Collection<InspectionStatus> overdueStatuses
    );
}
