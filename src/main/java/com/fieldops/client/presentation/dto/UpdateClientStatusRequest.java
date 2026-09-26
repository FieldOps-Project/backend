package com.fieldops.client.presentation.dto;

import com.fieldops.client.domain.model.ClientStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateClientStatusRequest", description = "Payload used to change a client status.")
public record UpdateClientStatusRequest(
        @NotNull
        @Schema(example = "INACTIVE")
        ClientStatus status
) {
}
