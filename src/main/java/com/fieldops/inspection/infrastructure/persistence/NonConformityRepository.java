package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.dashboard.infrastructure.persistence.NonConformitySeverityCountProjection;
import com.fieldops.inspection.domain.model.NonConformity;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.domain.model.NonConformityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface NonConformityRepository extends JpaRepository<NonConformity, UUID> {

    Optional<NonConformity> findByIdAndInspectionId(UUID id, UUID inspectionId);

    List<NonConformity> findAllByInspectionIdAndSeverity(UUID inspectionId, NonConformitySeverity severity);

    List<NonConformity> findAllByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

    @Query("""
            select n from NonConformity n, Inspection i
            where n.inspectionId = i.id
              and (:inspectionId is null or n.inspectionId = :inspectionId)
              and (:severity is null or n.severity = :severity)
              and (:status is null or n.status = :status)
              and (:clientId is null or i.clientId = :clientId)
              and (:technicianId is null or i.technicianId = :technicianId)
              and (:supervisorId is null or i.supervisorId = :supervisorId)
            order by n.serverReceivedAt asc
            """)
    Stream<NonConformity> streamForExport(
            @Param("inspectionId") UUID inspectionId,
            @Param("severity") NonConformitySeverity severity,
            @Param("status") NonConformityStatus status,
            @Param("clientId") UUID clientId,
            @Param("technicianId") UUID technicianId,
            @Param("supervisorId") UUID supervisorId
    );

    @Query("""
            select n from NonConformity n, Inspection i
            where n.inspectionId = i.id
              and n.inspectionId in :inspectionIds
              and (:inspectionId is null or n.inspectionId = :inspectionId)
              and (:severity is null or n.severity = :severity)
              and (:status is null or n.status = :status)
              and (:clientId is null or i.clientId = :clientId)
            """)
    Page<NonConformity> search(
            @Param("inspectionIds") Collection<UUID> inspectionIds,
            @Param("inspectionId") UUID inspectionId,
            @Param("severity") NonConformitySeverity severity,
            @Param("status") NonConformityStatus status,
            @Param("clientId") UUID clientId,
            Pageable pageable
    );

    @Query("""
            select n.severity as severity, count(n.id) as count
            from NonConformity n, Inspection i
            where n.inspectionId = i.id
              and (:periodStart is null or i.scheduledFor >= :periodStart)
              and (:periodEnd is null or i.scheduledFor <= :periodEnd)
              and (:supervisorId is null or i.supervisorId = :supervisorId)
            group by n.severity
            order by n.severity
            """)
    List<NonConformitySeverityCountProjection> countBySeverityForDashboard(
            @Param("periodStart") java.time.Instant periodStart,
            @Param("periodEnd") java.time.Instant periodEnd,
            @Param("supervisorId") UUID supervisorId
    );
}
