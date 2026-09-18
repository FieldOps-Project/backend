package com.fieldops.client.presentation.dto;

import com.fieldops.client.domain.model.InspectionSiteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateInspectionSiteStatusRequest", description = "Payload used to change a site status.")
public record UpdateInspectionSiteStatusRequest(
        @NotNull
        @Schema(example = "INACTIVE")
        InspectionSiteStatus status
) {
}
