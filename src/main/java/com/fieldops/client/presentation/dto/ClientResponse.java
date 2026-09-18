package com.fieldops.client.presentation.dto;

import com.fieldops.client.domain.model.ClientStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ClientResponse", description = "Public representation of a client.")
public record ClientResponse(
        @Schema(example = "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed")
        UUID id,

        @Schema(example = "Industria Alfa")
        String name,

        @Schema(example = "Industria Alfa Ltda", nullable = true)
        String legalName,

        @Schema(example = "12345678000190", nullable = true)
        String document,

        @Schema(example = "contato@industria-alfa.com.br", nullable = true)
        String email,

        @Schema(example = "+55 11 99999-0000", nullable = true)
        String phone,

        @Schema(example = "ACTIVE")
        ClientStatus status,

        Instant createdAt,

        Instant updatedAt,

        @Schema(example = "0")
        int version
) {
}
