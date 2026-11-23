package com.fieldops.inspection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "EvidenceResponse")
public record EvidenceResponse(
        UUID id,
        UUID inspectionId,
        UUID responseId,
        UUID nonConformityId,
        String type,
        String mimeType,
        long sizeBytes,
        String checksum,
        String description,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyMeters,
        Instant capturedAtDevice,
        Instant serverReceivedAt,
        Instant uploadedAt,
        UUID createdBy,
        Instant deletedAt
) {
}
