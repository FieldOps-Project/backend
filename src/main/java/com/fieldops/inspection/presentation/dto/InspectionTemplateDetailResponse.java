package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.TemplateStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "InspectionTemplateDetailResponse", description = "Template metadata with the draft structure being edited.")
public record InspectionTemplateDetailResponse(
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

        @Schema(example = "9a7b6c5d-4e3f-4a1b-8c2d-1e0f9a8b7c6d")
        UUID draftVersionId,

        List<TemplateSectionResponse> sections,

        UUID createdBy,

        Instant createdAt,

        Instant updatedAt,

        @Schema(example = "0")
        int version
) {
}
