package com.fieldops.inspection.presentation.dto;

import com.fieldops.inspection.domain.model.ResponseType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "InspectionItemSnapshotResponse", description = "Frozen checklist item captured at inspection scheduling time.")
public record InspectionItemSnapshotResponse(
        UUID id,
        UUID sourceTemplateItemId,
        String sectionTitle,
        String sectionDescription,
        int sectionOrder,
        String itemCode,
        String itemTitle,
        String itemDescription,
        ResponseType responseType,
        boolean required,
        String rulesJson,
        String optionsJson,
        int itemOrder,
        Instant createdAt
) {
}
