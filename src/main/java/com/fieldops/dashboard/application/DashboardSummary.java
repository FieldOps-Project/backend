package com.fieldops.dashboard.application;

import java.util.List;

public record DashboardSummary(
        long totalInspections,
        long overdueInspections,
        long inspectionsAwaitingReview,
        List<DashboardInspectionStatusCount> inspectionsByStatus,
        List<DashboardNonConformitySeverityCount> nonConformitiesBySeverity
) {
}
