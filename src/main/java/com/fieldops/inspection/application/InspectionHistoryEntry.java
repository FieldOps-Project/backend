package com.fieldops.inspection.application;

import java.time.Instant;
import java.util.UUID;

public record InspectionHistoryEntry(
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
