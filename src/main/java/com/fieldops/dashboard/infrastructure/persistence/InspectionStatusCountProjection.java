package com.fieldops.dashboard.infrastructure.persistence;

import com.fieldops.inspection.domain.model.InspectionStatus;

public interface InspectionStatusCountProjection {

    InspectionStatus getStatus();

    Long getCount();
}
