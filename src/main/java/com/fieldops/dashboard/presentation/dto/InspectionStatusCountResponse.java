package com.fieldops.dashboard.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "InspectionStatusCountResponse", description = "Inspection count grouped by status.")
public record InspectionStatusCountResponse(
        InspectionStatus status,
        long count
) {
}
