package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "InspectionLocationRequest", description = "Optional device position captured during a transition.")
public record InspectionLocationRequest(
        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        BigDecimal latitude,
        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        BigDecimal longitude,
        @PositiveOrZero
        BigDecimal accuracy,
        @NotNull
        Instant capturedAt
) {
}
