package com.fieldops.shared.domain.exception;

/**
 * Thrown when an operation conflicts with the current state of a resource
 * (e.g. duplicate unique key, optimistic lock failure).
 *
 * <p>Mapped to HTTP <strong>409 Conflict</strong> by the global handler.</p>
 */
public class ConflictException extends BusinessException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
