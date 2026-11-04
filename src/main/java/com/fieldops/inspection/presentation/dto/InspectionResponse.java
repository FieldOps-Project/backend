package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "InspectionResponse", description = "Scheduled inspection with its immutable checklist snapshot.")
public record InspectionResponse(
        UUID id,
        UUID templateVersionId,
        UUID clientId,
        UUID siteId,
        UUID equipmentId,
        UUID technicianId,
        UUID supervisorId,
        UUID createdBy,
        String title,
        String instructions,
        InspectionPriority priority,
        InspectionStatus status,
        Instant scheduledFor,
        Instant createdAt,
        Instant updatedAt,
        List<InspectionItemSnapshotResponse> itemSnapshots
) {
}
