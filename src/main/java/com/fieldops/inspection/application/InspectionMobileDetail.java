package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionMobileDetail(
        UUID id,
        String title,
        InspectionStatus status,
        InspectionPriority priority,
        Instant scheduledFor,
        Instant updatedAt,
        String instructions,
        int version,
        UUID clientId,
        String clientName,
        UUID siteId,
        String siteName,
        String addressLine,
        BigDecimal latitude,
        BigDecimal longitude,
        UUID equipmentId,
        String equipmentName,
        String equipmentQrCode,
        String equipmentAssetNumber,
        String equipmentSerialNumber,
        List<InspectionMobileChecklistItem> items,
        List<InspectionMobileAnswer> responses,
        InspectionMobileReview review
) {
}
