package com.fieldops.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TokenResponse", description = "Token renewal response.")
public record TokenResponse(
        @Schema(example = "token")
        String accessToken,

        @Schema(example = "token-de-renovacao")
        String refreshToken,

        @Schema(example = "900")
        long expiresIn
) {
}
