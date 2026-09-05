package com.fieldops.auth.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "TokenResponse", description = "Rotated access and refresh tokens.")
public record TokenResponse(
        String accessToken,
        String refreshToken,
        @Schema(example = "900")
        long expiresIn
) {
}
