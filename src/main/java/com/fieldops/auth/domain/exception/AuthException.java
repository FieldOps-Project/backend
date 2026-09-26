package com.fieldops.auth.domain.exception;

public class AuthException extends RuntimeException {

    private final String code;

    public AuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AuthException invalidCredentials() {
        return new AuthException("AUTH_INVALID_CREDENTIALS", "Invalid email or password");
    }

    public static AuthException inactiveUser() {
        return new AuthException(
                "AUTH_USER_INACTIVE",
                "Your account is inactive or blocked. Contact an administrator"
        );
    }

    public static AuthException invalidRefreshToken() {
        return new AuthException("AUTH_INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }
}
