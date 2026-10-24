package com.fieldops.inspection.domain.model;

/**
 * Business state of an inspection (documento 07, 7.4).
 *
 * <p>Not to be confused with the device synchronization state, which lives
 * only in the mobile app.</p>
 */
public enum InspectionStatus {
    DRAFT,
    ASSIGNED,
    IN_PROGRESS,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    CANCELED
}
