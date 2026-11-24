package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ApproveInspectionRequest(
        @Size(max = 2000)
        @Schema(description = "Optional reviewer comment", example = "Inspecao conferida sem pendencias.")
        String comments
) {
}
