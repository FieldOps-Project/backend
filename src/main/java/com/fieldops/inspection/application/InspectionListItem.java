package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;

import java.time.Instant;
import java.util.UUID;

/** Read model for the administrative inspection table. */
public record InspectionListItem(
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
        int answeredItems,
        int applicableItems,
        Instant updatedAt,
        boolean overdue
) {
}
