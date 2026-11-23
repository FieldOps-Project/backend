package com.fieldops.sync.domain.model;

public enum SyncOperationResultStatus {
    APPLIED,
    ALREADY_APPLIED,
    REJECTED,
    CONFLICT,
    DEPENDENCY_FAILED
}
