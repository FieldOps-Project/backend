package com.fieldops.inspection.infrastructure.persistence;

import java.util.UUID;

/** Aggregated snapshot count used by the administrative inspection list. */
public interface InspectionProgressProjection {

    UUID getInspectionId();

    long getApplicableItems();
}
