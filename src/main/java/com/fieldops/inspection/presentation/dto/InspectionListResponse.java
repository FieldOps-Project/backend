package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "InspectionListResponse", description = "Administrative inspection row with scope, progress and overdue status.")
public record InspectionListResponse(
        UUID id,
        String title,
        UUID clientId,
        String clientName,
        UUID siteId,
        String siteName,
        UUID equipmentId,
        String equipmentName,
        UUID technicianId,
        String technicianName,
        InspectionPriority priority,
        InspectionStatus status,
        Instant scheduledFor,
        @Schema(description = "Number of checklist items already answered.")
        int answeredItems,
        @Schema(description = "Number of applicable checklist items in the frozen snapshot.")
        int applicableItems,
        @Schema(description = "Answered items as a percentage from 0 to 100.", example = "0")
        int progress,
        Instant updatedAt,
        boolean overdue
) {
}
