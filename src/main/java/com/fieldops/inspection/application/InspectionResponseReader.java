package com.fieldops.inspection.application;

import java.util.List;
import java.util.UUID;

/**
 * Reads the current answers for an inspection. The checklist response module
 * supplies the persistence adapter in its own task.
 */
@FunctionalInterface
public interface InspectionResponseReader {

    List<InspectionExecutionAnswer> findByInspectionId(UUID inspectionId);
}
