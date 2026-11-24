package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "InspectionHistoryEntryResponse")
public record InspectionHistoryEntryResponse(
        UUID id,
        String action,
        String entityType,
        UUID entityId,
        UUID actorId,
        String actorName,
        Instant occurredAt,
        Instant deviceOccurredAt,
        String requestId,
        UUID deviceId,
        String previousValue,
        String newValue
) {
}
