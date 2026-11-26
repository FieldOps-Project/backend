package com.fieldops.dashboard.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "DashboardSummaryResponse", description = "Administrative inspection indicators for the selected period.")
public record DashboardSummaryResponse(
        long totalInspections,
        long overdueInspections,
        long inspectionsAwaitingReview,
        List<InspectionStatusCountResponse> inspectionsByStatus,
        List<NonConformitySeverityCountResponse> nonConformitiesBySeverity
) {
}
