package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.InspectionStatus;

import java.util.List;
import java.util.UUID;

public record InspectionMobileReview(
        InspectionStatus decision,
        String reason,
        List<UUID> itemsToCorrect
) {
}
