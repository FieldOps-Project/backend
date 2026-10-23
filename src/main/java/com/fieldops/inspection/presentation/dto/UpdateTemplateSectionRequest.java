package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
        name = "UpdateTemplateSectionRequest",
        description = "Payload used to update a draft section. Order changes use the batch reorder endpoint."
)
public record UpdateTemplateSectionRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(example = "Condicoes fisicas")
        String title,

        @Size(max = 1000)
        @Schema(example = "Estado do cilindro, lacre e manometro.", nullable = true)
        String description
) {
}
