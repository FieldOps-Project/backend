package com.fieldops.inspection.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateNonConformityRequest")
@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateNonConformityRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotNull NonConformitySeverity severity
) {
}
