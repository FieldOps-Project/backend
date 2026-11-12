package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignInspectionRequest(
        @NotNull
        @Schema(description = "Active user with the TECHNICIAN role", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID technicianId
) {
}
