package com.fieldops.client.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateClientRequest", description = "Payload used to update a client profile.")
public record UpdateClientRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(example = "Industria Alfa")
        String name,

        @Size(max = 255)
        @Schema(example = "Industria Alfa Ltda", nullable = true)
        String legalName,

        @Pattern(
                regexp = "^$|^[0-9]{11}$|^[0-9]{14}$|^[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}$|^[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}/[0-9]{4}-[0-9]{2}$",
                message = "must be a CPF or CNPJ with a valid format"
        )
        @Size(max = 18)
        @Schema(example = "12.345.678/0001-90", nullable = true)
        String document,

        @Email
        @Size(max = 320)
        @Schema(example = "contato@industria-alfa.com.br", nullable = true)
        String email,

        @Size(max = 32)
        @Schema(example = "+55 11 99999-0000", nullable = true)
        String phone
) {
}
