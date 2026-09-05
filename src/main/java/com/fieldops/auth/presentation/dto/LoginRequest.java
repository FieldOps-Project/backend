package com.fieldops.auth.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "Credentials used to start a session.")
public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(example = "maria.silva@example.com")
        String email,

        @NotBlank
        @Size(max = 128)
        @Schema(example = "Str0ngP@ssword", writeOnly = true)
        String password
) {
}
