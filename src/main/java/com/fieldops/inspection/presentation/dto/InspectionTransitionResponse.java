package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionStatus;

import java.time.Instant;
import java.util.UUID;

public record InspectionTransitionResponse(
        UUID id,
        InspectionStatus previousStatus,
        InspectionStatus status,
        UUID technicianId,
        UUID canceledBy,
        Instant canceledAt,
        String canceledReason
) {
}
