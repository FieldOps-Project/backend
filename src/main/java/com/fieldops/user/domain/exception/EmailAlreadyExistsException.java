package com.fieldops.user.domain.exception;

import com.fieldops.shared.domain.exception.ConflictException;

public class EmailAlreadyExistsException extends ConflictException {

    public static final String CODE = "EMAIL_ALREADY_EXISTS";

    public EmailAlreadyExistsException() {
        super(CODE, "A user with this email already exists");
    }
}
