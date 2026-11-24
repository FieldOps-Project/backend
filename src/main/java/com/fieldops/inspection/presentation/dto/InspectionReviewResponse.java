package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionReviewDecision;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "InspectionReviewResponse")
public record InspectionReviewResponse(
        UUID id,
        UUID inspectionId,
        int reviewCycle,
        UUID reviewerId,
        InspectionReviewDecision decision,
        String reason,
        String comments,
        List<UUID> itemsToCorrect,
        Instant reviewedAt,
        Instant createdAt
) {
}
