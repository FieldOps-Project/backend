package com.fieldops.user.presentation.dto;

import com.fieldops.user.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateUserStatusRequest", description = "Payload used to change a user status.")
public record UpdateUserStatusRequest(
        @NotNull
        @Schema(example = "INACTIVE")
        UserStatus status
) {
}
