package com.fieldops.dashboard.presentation.dto;

import com.fieldops.inspection.domain.model.NonConformitySeverity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "NonConformitySeverityCountResponse", description = "Non-conformity count grouped by severity.")
public record NonConformitySeverityCountResponse(
        NonConformitySeverity severity,
        long count
) {
}
