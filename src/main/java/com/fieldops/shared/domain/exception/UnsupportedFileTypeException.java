package com.fieldops.shared.domain.exception;

public class UnsupportedFileTypeException extends RuntimeException {

    private final String code;

    public UnsupportedFileTypeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
