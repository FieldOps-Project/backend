package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;

public record InspectionTransitionResult(
        Inspection inspection,
        InspectionStatus previousStatus
) {
}
