package com.fieldops.equipment.presentation.dto;

import com.fieldops.equipment.domain.model.EquipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "EquipmentResponse", description = "Equipment payload returned by the API.")
public record EquipmentResponse(
        UUID id,
        UUID siteId,
        String name,
        String assetNumber,
        String serialNumber,
        String manufacturer,
        String model,
        String description,
        String qrCode,
        EquipmentStatus status,
        LocalDate installedAt,
        Instant createdAt,
        Instant updatedAt,
        Integer version
) {
}
