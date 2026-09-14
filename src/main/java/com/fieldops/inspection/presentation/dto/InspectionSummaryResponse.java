package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "InspectionSummaryResponse", description = "Inspection summary data used by dashboard and mobile lists.")
public record InspectionSummaryResponse(
        @Schema(example = "3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5")
        UUID id,

        @Schema(example = "Inspecao de Extintor - Planta Sul")
        String title,

        @Schema(example = "IN_PROGRESS")
        String status,

        @Schema(example = "2026-09-14T10:00:00Z")
        OffsetDateTime scheduledAt
) {
}
