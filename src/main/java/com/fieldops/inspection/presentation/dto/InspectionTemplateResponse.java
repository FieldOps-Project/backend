package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.TemplateStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "InspectionTemplateResponse", description = "Template metadata used in listings.")
public record InspectionTemplateResponse(
        @Schema(example = "5b8e1f0a-2d3c-4e5f-8a9b-0c1d2e3f4a5b")
        UUID id,

        @Schema(example = "Checklist de extintores")
        String title,

        @Schema(example = "Inspecao mensal dos extintores de incendio.", nullable = true)
        String description,

        @Schema(example = "Seguranca contra incendio")
        String category,

        @Schema(example = "DRAFT")
        TemplateStatus status,

        @Schema(example = "1", nullable = true, description = "Latest published version number; null before the first publication.")
        Integer currentVersion,

        UUID createdBy,

        Instant createdAt,

        Instant updatedAt,

        @Schema(example = "0")
        int version
) {
}
