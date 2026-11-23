package com.fieldops.inspection.presentation.mapper;

import com.fieldops.inspection.application.InspectionListItem;
import com.fieldops.inspection.presentation.dto.InspectionListResponse;
import org.springframework.stereotype.Component;

@Component
public class InspectionListMapper {

    public InspectionListResponse toResponse(InspectionListItem item) {
        int progress = item.applicableItems() == 0
                ? 0
                : (int) Math.round(item.answeredItems() * 100.0 / item.applicableItems());

        return new InspectionListResponse(
                item.id(),
                item.title(),
                item.clientId(),
                item.clientName(),
                item.siteId(),
                item.siteName(),
                item.equipmentId(),
                item.equipmentName(),
                item.technicianId(),
                item.technicianName(),
                item.priority(),
                item.status(),
                item.scheduledFor(),
                item.answeredItems(),
                item.applicableItems(),
                progress,
                item.updatedAt(),
                item.overdue()
        );
    }
}
