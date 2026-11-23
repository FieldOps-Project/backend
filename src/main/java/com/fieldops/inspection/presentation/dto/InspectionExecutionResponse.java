package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "InspectionExecutionResponse", description = "Inspection transition with device/server timestamps.")
public record InspectionExecutionResponse(
        UUID id,
        InspectionStatus previousStatus,
        InspectionStatus status,
        UUID technicianId,
        Instant startedAtDevice,
        Instant startedAtServer,
        Instant completedAtDevice,
        Instant submittedAtServer,
        InspectionPositionResponse startPosition,
        InspectionPositionResponse completionPosition
) {
}
