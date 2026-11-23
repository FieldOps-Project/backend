package com.fieldops.sync.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(name = "SyncOperationRequest")
@JsonIgnoreProperties(ignoreUnknown = false)
public record SyncOperationRequest(
        @NotNull UUID operationId,
        @NotBlank String entityType,
        @NotNull UUID entityId,
        @NotBlank String operationType,
        @NotNull @Min(0) Integer baseVersion,
        List<UUID> dependsOn,
        @NotNull JsonNode payload
) {
    public List<UUID> dependencies() {
        return dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
