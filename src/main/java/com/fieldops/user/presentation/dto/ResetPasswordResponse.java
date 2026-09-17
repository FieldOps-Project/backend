package com.fieldops.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ResetPasswordResponse", description = "Temporary credential returned once after an administrator reset.")
public record ResetPasswordResponse(
        @Schema(example = "mJ7qP2vR9xK4nT6wL8sC", accessMode = Schema.AccessMode.READ_ONLY)
        String temporaryPassword
) {
}
