package com.fieldops.sync.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "SyncPullResponse")
public record SyncPullResponse(
        List<SyncChangeResponse> changes,
        String nextCursor,
        boolean hasMore,
        Instant serverTime
) {
}
