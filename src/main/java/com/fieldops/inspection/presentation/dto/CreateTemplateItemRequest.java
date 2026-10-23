package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.ResponseType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "CreateTemplateItemRequest", description = "Payload used to add an item to a draft section.")
public record CreateTemplateItemRequest(
        @Size(max = 50)
        @Schema(example = "EXT-03", nullable = true)
        String code,

        @NotBlank
        @Size(max = 255)
        @Schema(example = "A protecao do equipamento esta integra?")
        String title,

        @Size(max = 1000)
        @Schema(example = "Verifique trincas, ausencia de parafusos e partes soltas.", nullable = true)
        String description,

        @NotNull
        @Schema(example = "CONFORMITY")
        ResponseType responseType,

        @Schema(example = "true", nullable = true, description = "Defaults to true.")
        Boolean required,

        @Schema(example = "true", nullable = true, description = "Only for CONFORMITY and BOOLEAN. Defaults to false.")
        Boolean observationRequiredOnFailure,

        @Schema(example = "true", nullable = true, description = "Only for CONFORMITY and BOOLEAN. Defaults to false.")
        Boolean evidenceRequiredOnFailure,

        @Size(max = 50)
        @ArraySchema(
                arraySchema = @Schema(
                        nullable = true,
                        description = "Required for SINGLE_CHOICE (at least two unique labels); rejected for other types."
                ),
                schema = @Schema(example = "Bom")
        )
        List<@NotBlank @Size(max = 100) String> options,

        @Positive
        @Schema(
                example = "3",
                nullable = true,
                description = "1-based position. Following items shift down; omitted appends to the end."
        )
        Integer displayOrder
) {
}
