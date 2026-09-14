package com.fieldops.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Credentials payload used to authenticate a user.")
public record LoginRequest(
        @NotBlank
        @Email
        @Schema(example = "tecnico@fieldops.local")
        String email,

        @NotBlank
        @Schema(example = "senha-informada-pelo-usuario", writeOnly = true)
        String password
) {
}
