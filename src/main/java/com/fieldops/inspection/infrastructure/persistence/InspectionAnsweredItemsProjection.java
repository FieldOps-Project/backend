package com.fieldops.inspection.infrastructure.persistence;

import java.util.UUID;

/** Aggregated response count used by the administrative inspection list. */
public interface InspectionAnsweredItemsProjection {

    UUID getInspectionId();

    long getAnsweredItems();
}
