package com.fieldops.inspection.presentation.dto;

import com.fieldops.shared.presentation.dto.ApiError;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "InspectionResponseBatchItemResponse")
public record InspectionResponseBatchItemResponse(
        UUID id,
        BatchItemStatus status,
        InspectionResponseResponse response,
        String errorCode,
        String errorMessage,
        List<ApiError.FieldError> fieldErrors
) {
    public enum BatchItemStatus {
        APPLIED,
        REJECTED
    }
}
