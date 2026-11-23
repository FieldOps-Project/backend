package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "InspectionPositionResponse")
public record InspectionPositionResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracy,
        Instant capturedAt
) {
}
