package com.fieldops.equipment.presentation.mapper;

import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.presentation.dto.EquipmentResponse;
import org.springframework.stereotype.Component;

@Component
public class EquipmentMapper {

    public EquipmentResponse toResponse(Equipment equipment) {
        if (equipment == null) {
            return null;
        }
        return new EquipmentResponse(
                equipment.getId(),
                equipment.getSiteId(),
                equipment.getName(),
                equipment.getAssetNumber(),
                equipment.getSerialNumber(),
                equipment.getManufacturer(),
                equipment.getModel(),
                equipment.getDescription(),
                equipment.getQrCode(),
                equipment.getStatus(),
                equipment.getInstalledAt(),
                equipment.getCreatedAt(),
                equipment.getUpdatedAt(),
                equipment.getVersion()
        );
    }
}
