package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateInspectionTemplateRequest", description = "Payload used to create a template with an empty draft.")
public record CreateInspectionTemplateRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(example = "Checklist de extintores")
        String title,

        @Size(max = 1000)
        @Schema(example = "Inspecao mensal dos extintores de incendio.", nullable = true)
        String description,

        @NotBlank
        @Size(max = 100)
        @Schema(example = "Seguranca contra incendio")
        String category
) {
}
