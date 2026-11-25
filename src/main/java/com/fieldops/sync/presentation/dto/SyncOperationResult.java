package com.fieldops.sync.presentation.dto;

import com.fieldops.sync.domain.model.SyncOperationResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@Schema(name = "SyncOperationResult")
public record SyncOperationResult(
        UUID operationId,
        SyncOperationResultStatus status,
        Integer entityVersion,
        String errorCode,
        String errorMessage,
        JsonNode serverState
) {
    public SyncOperationResult(
            UUID operationId,
            SyncOperationResultStatus status,
            Integer entityVersion,
            String errorCode,
            String errorMessage
    ) {
        this(operationId, status, entityVersion, errorCode, errorMessage, null);
    }
}
