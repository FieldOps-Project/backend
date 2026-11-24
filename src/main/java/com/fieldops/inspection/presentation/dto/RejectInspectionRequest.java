package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record RejectInspectionRequest(
        @Size(max = 2000)
        @Schema(description = "Mandatory rejection reason; a blank value is refused with 422",
                example = "A fotografia do item 4 nao permite identificar o numero de serie.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,

        @Size(max = 2000)
        @Schema(description = "Optional reviewer comment")
        String comments,

        @Schema(description = "Snapshot item ids the technician must correct; empty reopens the whole inspection",
                example = "[\"ab18a638-1c45-4a15-a444-b4235506e912\"]")
        List<UUID> itemsToCorrect
) {
}
