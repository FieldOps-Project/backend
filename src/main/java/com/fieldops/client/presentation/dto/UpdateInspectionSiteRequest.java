package com.fieldops.client.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "UpdateInspectionSiteRequest", description = "Payload used to update a site without changing its client.")
public record UpdateInspectionSiteRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(example = "Unidade Sorocaba")
        String name,

        @Size(max = 1000)
        @Schema(example = "Fabrica principal", nullable = true)
        String description,

        @Size(max = 255)
        @Schema(example = "Av. das Industrias, 1000", nullable = true)
        String addressLine,

        @Size(max = 100)
        @Schema(example = "Sorocaba", nullable = true)
        String city,

        @Size(max = 100)
        @Schema(example = "SP", nullable = true)
        String state,

        @Size(max = 20)
        @Schema(example = "18000-000", nullable = true)
        String postalCode,

        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        @Schema(example = "-23.5015", nullable = true)
        BigDecimal latitude,

        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        @Schema(example = "-47.4526", nullable = true)
        BigDecimal longitude,

        @Size(max = 255)
        @Schema(example = "Joao da Silva", nullable = true)
        String contactName,

        @Size(max = 32)
        @Schema(example = "+55 15 99999-0000", nullable = true)
        String contactPhone
) {
}
