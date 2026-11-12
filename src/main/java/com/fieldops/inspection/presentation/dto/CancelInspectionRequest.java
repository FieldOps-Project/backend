package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelInspectionRequest(
        @NotBlank
        @Size(max = 1000)
        @Schema(description = "Business reason for cancellation", example = "Client site unavailable",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String reason
) {
}
