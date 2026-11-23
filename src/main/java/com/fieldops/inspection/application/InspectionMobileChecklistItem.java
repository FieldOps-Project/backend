package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.ResponseType;

import java.util.UUID;

public record InspectionMobileChecklistItem(
        UUID id,
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
        int itemOrder
) {
}
