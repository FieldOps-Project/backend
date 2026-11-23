package com.fieldops.inspection.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fieldops.inspection.domain.model.InspectionResponseConformity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "InspectionResponseResponse")
public record InspectionResponseResponse(
        UUID id,
        UUID inspectionItemId,
        String valueText,
        BigDecimal valueNumber,
        Boolean valueBoolean,
        LocalDate valueDate,
        JsonNode valueJson,
        InspectionResponseConformity conformity,
        String observation,
        Instant answeredAtDevice,
        Instant answeredAtServer,
        UUID answeredBy,
        int baseVersion,
        int version
) {
}
