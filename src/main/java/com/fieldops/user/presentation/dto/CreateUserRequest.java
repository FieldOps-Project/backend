package com.fieldops.user.presentation.dto;

import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateUserRequest", description = "Payload used to create a FieldOps user.")
public record CreateUserRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(example = "Maria Silva")
        String name,

        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(example = "maria.silva@example.com")
        String email,

        @NotBlank
        @Size(min = 8, max = 128)
        @Schema(example = "Str0ngP@ssword", writeOnly = true)
        String password,

        @NotNull
        @Schema(example = "TECHNICIAN")
        UserRole role,

        @Schema(example = "ACTIVE", nullable = true)
        UserStatus status,

        @Size(max = 32)
        @Schema(example = "+55 11 99999-0000", nullable = true)
        String phone
) {
}
