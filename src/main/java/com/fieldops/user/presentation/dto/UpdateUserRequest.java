package com.fieldops.user.presentation.dto;

import com.fieldops.user.domain.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateUserRequest", description = "Payload used to update a user's profile.")
public record UpdateUserRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(example = "Maria Silva")
        String name,

        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(example = "maria.silva@example.com")
        String email,

        @NotNull
        @Schema(example = "SUPERVISOR")
        UserRole role,

        @Size(max = 32)
        @Schema(example = "+55 11 99999-0000", nullable = true)
        String phone
) {
}
