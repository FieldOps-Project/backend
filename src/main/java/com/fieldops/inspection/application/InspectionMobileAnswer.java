package com.fieldops.inspection.application;

import java.util.List;
import java.util.UUID;

public record InspectionMobileAnswer(
        UUID itemId,
        String valueJson,
        String observation,
        List<UUID> evidenceIds
) {
}
