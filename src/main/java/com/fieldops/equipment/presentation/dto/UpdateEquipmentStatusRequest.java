package com.fieldops.equipment.presentation.dto;

import com.fieldops.equipment.domain.model.EquipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateEquipmentStatusRequest", description = "Payload used to update an equipment's operational status.")
public record UpdateEquipmentStatusRequest(
        @NotNull
        @Schema(example = "INACTIVE")
        EquipmentStatus status
) {
}
