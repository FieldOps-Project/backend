package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionStatus;

import java.util.List;
import java.util.UUID;

public record MobileInspectionReviewResponse(
        InspectionStatus decision,
        String reason,
        List<UUID> itemsToCorrect
) {
}
