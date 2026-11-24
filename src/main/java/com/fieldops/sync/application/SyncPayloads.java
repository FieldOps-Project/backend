package com.fieldops.sync.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import com.fieldops.inspection.domain.model.InspectionResponseConformity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

final class SyncPayloads {

    private SyncPayloads() {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record Response(
            UUID inspectionId,
            UUID inspectionItemId,
            String valueText,
            BigDecimal valueNumber,
            Boolean valueBoolean,
            LocalDate valueDate,
            JsonNode valueJson,
            InspectionResponseConformity conformity,
            String observation,
            Instant answeredAtDevice
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record NonConformity(
            UUID inspectionId,
            UUID inspectionItemId,
            UUID responseId,
            String title,
            String description,
            com.fieldops.inspection.domain.model.NonConformitySeverity severity,
            Instant createdAtDevice
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record Evidence(
            UUID inspectionId,
            UUID responseId,
            UUID nonConformityId,
            String type,
            String mimeType,
            Instant capturedAtDevice,
            String description,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyMeters,
            String contentBase64
    ) {
    }
}
