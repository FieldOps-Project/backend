package com.fieldops.shared.presentation.dto;

import java.time.Instant;
import java.util.List;

/**
 * Canonical error envelope returned by every error response of the API.
 *
 * <p>Follows the contract defined in the project specification:
 * timestamp, HTTP status, machine-readable code, human message,
 * request path, correlation id and optional field-level errors.</p>
 *
 * @param timestamp   moment the error occurred (UTC)
 * @param status      HTTP status code
 * @param code        machine-readable error code (e.g. {@code "VALIDATION_ERROR"})
 * @param message     human-readable description
 * @param path        request URI that caused the error
 * @param requestId   correlation id for log tracing
 * @param fieldErrors per-field validation errors (may be empty)
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldError> fieldErrors
) {

    /**
     * Represents a single field-level validation error.
     *
     * @param field   the field name that failed validation
     * @param message description of the violation
     */
    public record FieldError(String field, String message) {}
}
