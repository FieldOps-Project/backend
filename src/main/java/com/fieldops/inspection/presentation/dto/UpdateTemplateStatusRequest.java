package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.TemplateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateTemplateStatusRequest", description = "Activates or retires a published template.")
public record UpdateTemplateStatusRequest(
        @NotNull
        @Schema(example = "INACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
        TemplateStatus status
) {
}
