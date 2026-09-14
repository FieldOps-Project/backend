package com.fieldops.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginResponse", description = "Authentication payload returned after successful login.")
public record LoginResponse(
        @Schema(example = "token")
        String accessToken,

        @Schema(example = "token-de-renovacao")
        String refreshToken,

        @Schema(example = "900")
        long expiresIn,

        AuthenticatedUserResponse user
) {
}
