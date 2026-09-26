package com.fieldops.equipment.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "UpdateEquipmentRequest", description = "Payload used to update equipment details.")
public record UpdateEquipmentRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(example = "Compressor de Ar Industrial 20 HP")
        String name,

        @Size(max = 100)
        @Schema(example = "AST-2026-001-REV")
        String assetNumber,

        @Size(max = 100)
        @Schema(example = "SN-99887766")
        String serialNumber,

        @Size(max = 255)
        @Schema(example = "Schulz")
        String manufacturer,

        @Size(max = 255)
        @Schema(example = "MSV 20 MAX II")
        String model,

        @Size(max = 1000)
        @Schema(example = "Compressor revisado e reconfigurado")
        String description,

        @NotBlank
        @Size(max = 255)
        @Schema(example = "EQP-SITE-001-COMP-01")
        String qrCode,

        @Schema(example = "2026-01-15")
        LocalDate installedAt
) {
}
