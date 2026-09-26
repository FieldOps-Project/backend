package com.fieldops.auth.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
@Schema(name = "RefreshTokenRequest", description = "Refresh token used to rotate a session.")
public record RefreshTokenRequest(
        @NotBlank
        @Size(max = 512)
        @Schema(example = "opaque-refresh-token", writeOnly = true)
        String refreshToken
)
{
}
