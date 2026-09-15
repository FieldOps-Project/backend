package com.fieldops.shared.domain.exception;

/**
 * Base exception for all domain/business errors in the FieldOps API.
 *
 * <p>Every business exception carries a machine-readable {@code code}
 * (e.g. {@code "INSPECTION_ALREADY_SUBMITTED"}) that is forwarded to the
 * client inside the canonical {@code ApiError} envelope. Subclasses map
 * to specific HTTP status codes via the global exception handler.</p>
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** Machine-readable error code forwarded to the API consumer. */
    public String getCode() {
        return code;
    }
}
