package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.ResponseType;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record MobileInspectionItemResponse(
        UUID id,
        String sectionTitle,
        String sectionDescription,
        int sectionOrder,
        String itemCode,
        String itemTitle,
        String itemDescription,
        ResponseType responseType,
        boolean required,
        JsonNode rules,
        JsonNode options,
        int itemOrder
) {
}
