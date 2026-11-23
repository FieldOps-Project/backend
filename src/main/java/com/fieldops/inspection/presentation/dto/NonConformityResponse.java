package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.domain.model.NonConformityStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "NonConformityResponse")
public record NonConformityResponse(
        UUID id,
        UUID inspectionId,
        UUID inspectionItemId,
        UUID responseId,
        String title,
        String description,
        NonConformitySeverity severity,
        NonConformityStatus status,
        UUID createdBy,
        Instant createdAtDevice,
        Instant serverReceivedAt,
        Instant createdAt,
        Instant updatedAt,
        int version
) {
}
