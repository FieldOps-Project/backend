package com.fieldops.inspection.presentation.mapper;

import com.fieldops.inspection.domain.model.NonConformity;
import com.fieldops.inspection.presentation.dto.NonConformityResponse;
import org.springframework.stereotype.Component;

@Component
public class NonConformityMapper {

    public NonConformityResponse toResponse(NonConformity value) {
        return new NonConformityResponse(
                value.getId(), value.getInspectionId(), value.getInspectionItemId(), value.getResponseId(),
                value.getTitle(), value.getDescription(), value.getSeverity(), value.getStatus(),
                value.getCreatedBy(), value.getCreatedAtDevice(), value.getServerReceivedAt(),
                value.getCreatedAt(), value.getUpdatedAt(), value.getVersion()
        );
    }
}
