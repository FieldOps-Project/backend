package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.InspectionReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionReviewRepository extends JpaRepository<InspectionReview, UUID> {

    @EntityGraph(attributePaths = "itemsToCorrect")
    List<InspectionReview> findAllByInspectionIdOrderByReviewCycleAsc(UUID inspectionId);

    @EntityGraph(attributePaths = "itemsToCorrect")
    Optional<InspectionReview> findFirstByInspectionIdOrderByReviewCycleDesc(UUID inspectionId);

    @Query("select coalesce(max(r.reviewCycle), 0) from InspectionReview r where r.inspectionId = :inspectionId")
    int findLastReviewCycle(@Param("inspectionId") UUID inspectionId);
}
