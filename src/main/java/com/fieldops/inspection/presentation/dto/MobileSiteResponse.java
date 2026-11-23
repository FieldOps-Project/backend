package com.fieldops.inspection.presentation.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MobileSiteResponse(
        UUID id,
        String name,
        String addressLine,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
