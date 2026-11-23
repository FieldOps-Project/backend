package com.fieldops.inspection.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fieldops.inspection.domain.model.NonConformityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateNonConformityStatusRequest")
@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateNonConformityStatusRequest(@NotNull NonConformityStatus status) {
}
