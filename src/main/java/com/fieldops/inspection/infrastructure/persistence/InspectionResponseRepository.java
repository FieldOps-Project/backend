package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.InspectionResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionResponseRepository extends JpaRepository<InspectionResponse, UUID> {

    List<InspectionResponse> findAllByInspectionIdOrderByAnsweredAtServerAsc(UUID inspectionId);

    long countByInspectionId(UUID inspectionId);

    @Query("""
            select r.inspectionId as inspectionId, count(r.id) as answeredItems
            from InspectionResponse r
            where r.inspectionId in :inspectionIds
            group by r.inspectionId
            """)
    List<InspectionAnsweredItemsProjection> countAnsweredItems(@Param("inspectionIds") Collection<UUID> inspectionIds);

    Optional<InspectionResponse> findByIdAndInspectionId(UUID id, UUID inspectionId);

    Optional<InspectionResponse> findByInspectionIdAndInspectionItemId(UUID inspectionId, UUID inspectionItemId);
}
