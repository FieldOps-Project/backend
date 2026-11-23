package com.fieldops.inspection.presentation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "CreateNonConformityRequest")
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateNonConformityRequest(
        @JsonAlias("nonConformityId") @NotNull UUID id,
        UUID inspectionItemId,
        UUID responseId,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotNull NonConformitySeverity severity,
        @NotNull Instant createdAtDevice
) {
}
