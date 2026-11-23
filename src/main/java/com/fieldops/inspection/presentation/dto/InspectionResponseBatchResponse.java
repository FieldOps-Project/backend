package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "InspectionResponseBatchResponse")
public record InspectionResponseBatchResponse(
        List<InspectionResponseBatchItemResponse> results
) {
}
