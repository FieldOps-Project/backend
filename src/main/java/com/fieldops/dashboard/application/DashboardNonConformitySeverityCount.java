package com.fieldops.dashboard.application;

import com.fieldops.inspection.domain.model.NonConformitySeverity;

public record DashboardNonConformitySeverityCount(NonConformitySeverity severity, long count) {
}
