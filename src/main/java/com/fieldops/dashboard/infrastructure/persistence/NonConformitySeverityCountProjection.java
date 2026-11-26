package com.fieldops.dashboard.infrastructure.persistence;

import com.fieldops.inspection.domain.model.NonConformitySeverity;

public interface NonConformitySeverityCountProjection {

    NonConformitySeverity getSeverity();

    Long getCount();
}
