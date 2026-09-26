package com.fieldops.equipment.presentation.dto;

import com.fieldops.equipment.domain.model.EquipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "CreateEquipmentRequest", description = "Payload used to register new equipment.")
public record CreateEquipmentRequest(
        @NotNull
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID siteId,

        @NotBlank
        @Size(max = 255)
        @Schema(example = "Compressor de Ar Industrial")
        String name,

        @Size(max = 100)
        @Schema(example = "AST-2026-001")
        String assetNumber,

        @Size(max = 100)
        @Schema(example = "SN-99887766")
        String serialNumber,

        @Size(max = 255)
        @Schema(example = "Schulz")
        String manufacturer,

        @Size(max = 255)
        @Schema(example = "MSV 20 MAX")
        String model,

        @Size(max = 1000)
        @Schema(example = "Compressor de pistao 20 pcm instalado na sala de maquinas")
        String description,

        @NotBlank
        @Size(max = 255)
        @Schema(example = "EQP-SITE-001-COMP-01")
        String qrCode,

        @Schema(example = "ACTIVE")
        EquipmentStatus status,

        @Schema(example = "2026-01-15")
        LocalDate installedAt
) {
}
