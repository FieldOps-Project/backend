package com.fieldops.inspection.application;

import java.util.UUID;

@FunctionalInterface
public interface InspectionNonConformityReader {

    boolean hasCriticalWithoutEvidence(UUID inspectionId);
}
