package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateTemplateVersionAvailabilityRequest", description = "Retires or restores a version for new scheduling.")
public record UpdateTemplateVersionAvailabilityRequest(
        @NotNull
        @Schema(example = "false")
        Boolean activeForNewInspections
) {
}
