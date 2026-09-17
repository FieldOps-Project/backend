package com.fieldops.auth.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "LoginResponse", description = "Tokens and user identity returned after login.")
public record LoginResponse(
        String accessToken,
        String refreshToken,
        @Schema(example = "900")
        long expiresIn,
        AuthUserResponse user
) {
}
