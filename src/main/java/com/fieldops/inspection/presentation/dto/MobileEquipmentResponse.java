package com.fieldops.inspection.presentation.dto;

import java.util.UUID;

public record MobileEquipmentResponse(
        UUID id,
        String name,
        String qrCode,
        String assetNumber,
        String serialNumber
) {
}
