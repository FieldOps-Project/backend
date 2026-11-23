package com.fieldops.sync.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record SyncChange(
        String entityType,
        UUID entityId,
        String changeType,
        int version,
        Instant changedAt,
        JsonNode payload
) {
    public SyncCursor cursor() {
        return new SyncCursor(changedAt, entityId);
    }
}
