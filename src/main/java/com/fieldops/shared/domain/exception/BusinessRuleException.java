package com.fieldops.shared.domain.exception;

/**
 * Thrown when a business rule is violated (e.g. trying to approve an
 * inspection that has not been submitted yet).
 *
 * <p>Mapped to HTTP <strong>422 Unprocessable Entity</strong> by the global handler.</p>
 */
public class BusinessRuleException extends BusinessException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
