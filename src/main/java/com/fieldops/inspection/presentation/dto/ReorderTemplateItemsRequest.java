package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(name = "ReorderTemplateItemsRequest", description = "Every item id of the section, in the desired order.")
public record ReorderTemplateItemsRequest(
        @NotEmpty
        @ArraySchema(schema = @Schema(example = "3c2d9a41-77e0-4a8e-a1f6-5b9e0d7c2e31"))
        List<@NotNull UUID> itemIds
) {
}
