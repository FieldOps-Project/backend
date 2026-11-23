package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;

import java.util.List;

@Schema(name = "InspectionResponseBatchRequest")
public record InspectionResponseBatchRequest(
        @NotEmpty List<@Valid SaveInspectionResponseRequest> responses
) {
}
