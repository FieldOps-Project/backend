package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "MobileInspectionResponse", description = "Complete inspection payload scoped to the authenticated technician.")
public record MobileInspectionResponse(
        UUID id,
        String title,
        InspectionStatus status,
        InspectionPriority priority,
        Instant scheduledFor,
        Instant updatedAt,
        String instructions,
        int version,
        MobileClientResponse client,
        MobileSiteResponse site,
        MobileEquipmentResponse equipment,
        List<MobileInspectionItemResponse> items,
        List<MobileInspectionAnswerResponse> responses,
        MobileInspectionReviewResponse review
) {
}
