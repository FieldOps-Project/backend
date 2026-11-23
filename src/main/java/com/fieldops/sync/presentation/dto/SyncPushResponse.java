package com.fieldops.sync.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "SyncPushResponse")
public record SyncPushResponse(
        List<SyncOperationResult> results,
        List<JsonNode> changes,
        String nextCursor,
        Instant serverTime
) {
}
