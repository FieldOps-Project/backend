package com.fieldops.sync.presentation.dto;

import com.fieldops.sync.domain.model.SyncOperationResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "SyncOperationResult")
public record SyncOperationResult(
        UUID operationId,
        SyncOperationResultStatus status,
        Integer entityVersion,
        String errorCode,
        String errorMessage
) {
}
