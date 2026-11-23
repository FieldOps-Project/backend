package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "CreateInspectionRequest", description = "Data used to schedule an inspection from an active published template version.")
public record CreateInspectionRequest(
        @NotNull UUID templateVersionId,
        @NotNull UUID clientId,
        @NotNull UUID siteId,
        @Schema(nullable = true, description = "Required unless the template category represents a site or environment inspection.")
        UUID equipmentId,
        @Schema(nullable = true, description = "When omitted, the inspection remains in DRAFT until a technician is assigned.")
        UUID technicianId,
        UUID supervisorId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String instructions,
        InspectionPriority priority,
        @NotNull Instant scheduledFor
) {
}
