package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.ResponseType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "TemplateItemResponse", description = "Checklist item of a template version.")
public record TemplateItemResponse(
        @Schema(example = "3c2d9a41-77e0-4a8e-a1f6-5b9e0d7c2e31")
        UUID id,

        @Schema(example = "8f0c7f5e-4c1b-4f43-9d56-0f4f2a7c1b10")
        UUID sectionId,

        @Schema(example = "EXT-03", nullable = true)
        String code,

        @Schema(example = "A protecao do equipamento esta integra?")
        String title,

        @Schema(example = "Verifique trincas, ausencia de parafusos e partes soltas.", nullable = true)
        String description,

        @Schema(example = "CONFORMITY")
        ResponseType responseType,

        @Schema(example = "true")
        boolean required,

        @Schema(example = "true")
        boolean observationRequiredOnFailure,

        @Schema(example = "true")
        boolean evidenceRequiredOnFailure,

        @Schema(description = "SINGLE_CHOICE labels; empty for other types.")
        List<String> options,

        @Schema(example = "3")
        int displayOrder
) {
}
