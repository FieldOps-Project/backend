package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InspectionItemSnapshotRepository extends JpaRepository<InspectionItemSnapshot, UUID> {

    List<InspectionItemSnapshot> findByInspectionIdOrderBySectionOrderAscItemOrderAsc(UUID inspectionId);

    @Query(value = """
            SELECT inspection_id AS inspectionId, COUNT(*) AS applicableItems
            FROM inspection_item_snapshots
            WHERE inspection_id IN (:inspectionIds)
            GROUP BY inspection_id
            """, nativeQuery = true)
    List<InspectionProgressProjection> countApplicableItems(@Param("inspectionIds") Collection<UUID> inspectionIds);

    @Query("select count(snapshot.id) from InspectionItemSnapshot snapshot where snapshot.inspection.id = :inspectionId")
    long countApplicableItemsByInspectionId(@Param("inspectionId") UUID inspectionId);
}
