package com.fieldops.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshRequest", description = "Payload used to renew an access token.")
public record RefreshRequest(
        @NotBlank
        @Schema(example = "token-de-renovacao")
        String refreshToken
) {
}
