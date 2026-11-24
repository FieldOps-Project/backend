package com.fieldops.inspection.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import com.fieldops.inspection.domain.model.InspectionResponseConformity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "SaveInspectionResponseRequest")
@JsonIgnoreProperties(ignoreUnknown = false)
public record SaveInspectionResponseRequest(
        @NotNull UUID id,
        @NotNull UUID inspectionItemId,
        String valueText,
        BigDecimal valueNumber,
        Boolean valueBoolean,
        LocalDate valueDate,
        JsonNode valueJson,
        InspectionResponseConformity conformity,
        String observation,
        @NotNull Instant answeredAtDevice,
        @NotNull @Min(0) Integer baseVersion
) {
}
