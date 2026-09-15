package com.fieldops.user.presentation.dto;

import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "UserResponse", description = "Public representation of a FieldOps user.")
public record UserResponse(
        @Schema(example = "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed")
        UUID id,

        @Schema(example = "Maria Silva")
        String name,

        @Schema(example = "maria.silva@example.com")
        String email,

        @Schema(example = "TECHNICIAN")
        UserRole role,

        @Schema(example = "ACTIVE")
        UserStatus status,

        @Schema(example = "+55 11 99999-0000", nullable = true)
        String phone,

        Instant createdAt,

        Instant updatedAt,

        @Schema(example = "0")
        int version
) {
}
