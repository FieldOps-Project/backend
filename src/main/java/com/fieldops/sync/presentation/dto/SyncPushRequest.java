package com.fieldops.sync.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(name = "SyncPushRequest")
@JsonIgnoreProperties(ignoreUnknown = false)
public record SyncPushRequest(
        @NotNull UUID deviceId,
        String lastPullCursor,
        @NotEmpty List<@Valid SyncOperationRequest> operations
) {
}
