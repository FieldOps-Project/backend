package com.fieldops.auth.presentation.dto;
import com.fieldops.user.domain.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
@Schema(name = "AuthUserResponse", description = "Public identity returned by authentication endpoints.")
public record AuthUserResponse(
        UUID id,
        String name,
        String email,
        UserRole role
) {
}
