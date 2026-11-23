package com.fieldops.sync.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fieldops.sync.domain.model.SyncChange;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "SyncChange")
public record SyncChangeResponse(
        String entityType,
        UUID entityId,
        String changeType,
        int version,
        JsonNode payload
) {
    public static SyncChangeResponse from(SyncChange change) {
        return new SyncChangeResponse(
                change.entityType(), change.entityId(), change.changeType(),
                change.version(), change.payload()
        );
    }
}
