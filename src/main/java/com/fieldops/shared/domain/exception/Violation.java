package com.fieldops.shared.domain.exception;

/**
 * A single rule violation attached to a business exception, rendered as a
 * {@code fieldErrors} entry of the canonical error envelope.
 *
 * @param field   path of the offending value, e.g. {@code sections[0].items[1].options}
 * @param message human-readable description of the violation
 */
public record Violation(String field, String message) {
}
