package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;

import java.time.Instant;
import java.util.UUID;

public record InspectionMobileSummary(
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
