package com.fieldops.inspection.application;

import java.util.UUID;

/**
 * Minimal response projection consumed by server-side submit validation.
 * The response persistence task can implement the reader without coupling it
 * to the execution transition service.
 */
public record InspectionExecutionAnswer(
        UUID itemId,
        boolean answered,
        String conformity,
        String observation,
        boolean hasEvidence
) {

    public boolean isNonConforming() {
        return "NON_CONFORMING".equalsIgnoreCase(conformity);
    }
}
