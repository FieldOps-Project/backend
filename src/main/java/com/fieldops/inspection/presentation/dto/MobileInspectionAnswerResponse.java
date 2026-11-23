package com.fieldops.inspection.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public record MobileInspectionAnswerResponse(
        UUID itemId,
        JsonNode value,
        String observation,
        List<UUID> evidenceIds
) {
}
