package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "MobileInspectionSummaryResponse", description = "Inspection summary scoped to the authenticated technician.")
public record MobileInspectionSummaryResponse(
        UUID id,
        String title,
        InspectionStatus status,
        InspectionPriority priority,
        Instant scheduledFor,
        Instant updatedAt,
        int version,
        UUID clientId,
        String clientName,
        UUID siteId,
        String siteName,
        UUID equipmentId,
        String equipmentName
) {
}
