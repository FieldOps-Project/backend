package com.fieldops.inspection.presentation.mapper;

import com.fieldops.inspection.application.InspectionHistoryEntry;
import com.fieldops.inspection.presentation.dto.InspectionHistoryEntryResponse;
import org.springframework.stereotype.Component;

@Component
public class InspectionHistoryMapper {

    public InspectionHistoryEntryResponse toResponse(InspectionHistoryEntry entry) {
        return new InspectionHistoryEntryResponse(
                entry.id(), entry.action(), entry.entityType(), entry.entityId(), entry.actorId(), entry.actorName(),
                entry.occurredAt(), entry.deviceOccurredAt(), entry.requestId(), entry.deviceId(),
                entry.previousValue(), entry.newValue()
        );
    }
}
