package com.fieldops.shared.domain.exception;

import java.util.List;

/**
 * Thrown when a business rule is violated (e.g. trying to approve an
 * inspection that has not been submitted yet).
 *
 * <p>Mapped to HTTP <strong>422 Unprocessable Entity</strong> by the global handler.
 * When several rules fail at once, every {@link Violation} is returned in
 * {@code fieldErrors}.</p>
 */
public class BusinessRuleException extends BusinessException {

    private final List<Violation> violations;

    public BusinessRuleException(String code, String message) {
        this(code, message, List.of());
    }

    public BusinessRuleException(String code, String message, List<Violation> violations) {
        super(code, message);
        this.violations = List.copyOf(violations);
    }

    public List<Violation> getViolations() {
        return violations;
    }
}
