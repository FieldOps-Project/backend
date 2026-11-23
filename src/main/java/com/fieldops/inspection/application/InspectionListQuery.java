package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;

import java.time.Instant;
import java.util.UUID;

/** Filters accepted by the administrative inspection list. */
public record InspectionListQuery(
        String search,
        InspectionStatus status,
        UUID technicianId,
        UUID supervisorId,
        UUID clientId,
        UUID siteId,
        UUID equipmentId,
        InspectionPriority priority,
        Instant scheduledFrom,
        Instant scheduledTo,
        boolean overdue,
        int page,
        int size,
        String sort
) {
}
