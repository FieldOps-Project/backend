package com.fieldops.shared.domain.exception;

/**
 * Thrown when a request is syntactically valid but contains an unsupported
 * value, such as a field outside an endpoint's allowed sort list.
 */
public class InvalidRequestException extends RuntimeException {

    private final String code;

    public InvalidRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
