package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "TemplateVersionSummaryResponse", description = "Published, immutable template version.")
public record TemplateVersionSummaryResponse(
        @Schema(example = "2e4c6a8b-0d1f-4a3c-9e5b-7d9f1b3d5f7a")
        UUID id,

        @Schema(example = "5b8e1f0a-2d3c-4e5f-8a9b-0c1d2e3f4a5b")
        UUID templateId,

        @Schema(example = "1")
        int versionNumber,

        @Schema(example = "Checklist de extintores")
        String title,

        UUID publishedBy,

        Instant publishedAt,

        @Schema(example = "true", description = "Whether new inspections can be scheduled with this version.")
        boolean activeForNewInspections
) {
}
