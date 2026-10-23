package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateTemplateSectionRequest", description = "Payload used to add a section to the draft.")
public record CreateTemplateSectionRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(example = "Condicoes fisicas")
        String title,

        @Size(max = 1000)
        @Schema(example = "Estado do cilindro, lacre e manometro.", nullable = true)
        String description,

        @Positive
        @Schema(
                example = "2",
                nullable = true,
                description = "1-based position. Following sections shift down; omitted appends to the end."
        )
        Integer displayOrder
) {
}
