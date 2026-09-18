package com.fieldops.client.presentation.dto;

import com.fieldops.client.domain.model.InspectionSiteStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "InspectionSiteResponse", description = "Public representation of a site linked to a client.")
public record InspectionSiteResponse(
        @Schema(example = "7ae3e623-419b-4adf-8da4-7f9bd7c3dd35")
        UUID id,

        @Schema(example = "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed")
        UUID clientId,

        @Schema(example = "Unidade Sorocaba")
        String name,

        @Schema(example = "Fabrica principal", nullable = true)
        String description,

        @Schema(example = "Av. das Industrias, 1000", nullable = true)
        String addressLine,

        @Schema(example = "Sorocaba", nullable = true)
        String city,

        @Schema(example = "SP", nullable = true)
        String state,

        @Schema(example = "18000-000", nullable = true)
        String postalCode,

        @Schema(example = "-23.5015", nullable = true)
        BigDecimal latitude,

        @Schema(example = "-47.4526", nullable = true)
        BigDecimal longitude,

        @Schema(example = "Joao da Silva", nullable = true)
        String contactName,

        @Schema(example = "+55 15 99999-0000", nullable = true)
        String contactPhone,

        @Schema(example = "ACTIVE")
        InspectionSiteStatus status,

        Instant createdAt,

        Instant updatedAt,

        @Schema(example = "0")
        int version
) {
}
