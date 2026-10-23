package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "TemplateSectionResponse", description = "Section of a template version with its ordered items.")
public record TemplateSectionResponse(
        @Schema(example = "8f0c7f5e-4c1b-4f43-9d56-0f4f2a7c1b10")
        UUID id,

        @Schema(example = "Condicoes fisicas")
        String title,

        @Schema(example = "Estado do cilindro, lacre e manometro.", nullable = true)
        String description,

        @Schema(example = "1")
        int displayOrder,

        List<TemplateItemResponse> items
) {
}
