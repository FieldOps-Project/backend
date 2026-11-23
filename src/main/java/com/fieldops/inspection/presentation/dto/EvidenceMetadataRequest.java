package com.fieldops.inspection.presentation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "EvidenceMetadataRequest")
@JsonIgnoreProperties(ignoreUnknown = false)
public record EvidenceMetadataRequest(
        @JsonAlias("evidenceId") @NotNull UUID id,
        UUID responseId,
        UUID nonConformityId,
        @NotBlank @Size(max = 32) String type,
        @NotNull Instant capturedAtDevice,
        @Size(max = 2000) String description,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyMeters
) {
}
