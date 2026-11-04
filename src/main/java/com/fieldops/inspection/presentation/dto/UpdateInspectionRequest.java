package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.InspectionPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "UpdateInspectionRequest", description = "Planning data editable before an inspection starts. The checklist snapshot is never regenerated.")
public record UpdateInspectionRequest(
        @NotNull UUID clientId,
        @NotNull UUID siteId,
        UUID equipmentId,
        UUID technicianId,
        UUID supervisorId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String instructions,
        InspectionPriority priority,
        @NotNull Instant scheduledFor
) {
}
