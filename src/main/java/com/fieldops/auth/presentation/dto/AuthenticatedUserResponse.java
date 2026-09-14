package com.fieldops.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "AuthenticatedUserResponse", description = "Minimal authenticated user representation.")
public record AuthenticatedUserResponse(
        @Schema(example = "8a50e30d-0000-0000-0000-000000000000")
        UUID id,

        @Schema(example = "Carlos Tecnico")
        String name,

        @Schema(example = "tecnico@fieldops.local")
        String email,

        @Schema(example = "TECHNICIAN")
        String role
) {
}
