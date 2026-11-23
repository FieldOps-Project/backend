package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.InspectionStatus;

import java.time.Instant;

/** Incremental filters for the technician inspection feed. */
public record InspectionMobileQuery(
        InspectionStatus status,
        Instant updatedSince
) {
}
