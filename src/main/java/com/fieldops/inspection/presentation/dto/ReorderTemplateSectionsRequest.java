package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(name = "ReorderTemplateSectionsRequest", description = "Every draft section id, in the desired order.")
public record ReorderTemplateSectionsRequest(
        @NotEmpty
        @ArraySchema(schema = @Schema(example = "8f0c7f5e-4c1b-4f43-9d56-0f4f2a7c1b10"))
        List<@NotNull UUID> sectionIds
) {
}
