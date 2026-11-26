package com.fieldops.dashboard.application;

import com.fieldops.inspection.domain.model.InspectionStatus;

public record DashboardInspectionStatusCount(InspectionStatus status, long count) {
}
