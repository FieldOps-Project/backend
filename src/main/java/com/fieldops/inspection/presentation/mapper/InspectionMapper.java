package com.fieldops.inspection.presentation.mapper;

import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.presentation.dto.InspectionItemSnapshotResponse;
import com.fieldops.inspection.presentation.dto.InspectionResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class InspectionMapper {

    public InspectionResponse toResponse(Inspection inspection) {
        return new InspectionResponse(
                inspection.getId(),
                inspection.getTemplateVersionId(),
                inspection.getClientId(),
                inspection.getSiteId(),
                inspection.getEquipmentId(),
                inspection.getTechnicianId(),
                inspection.getSupervisorId(),
                inspection.getCreatedBy(),
                inspection.getTitle(),
                inspection.getInstructions(),
                inspection.getPriority(),
                inspection.getStatus(),
                inspection.getScheduledFor(),
                inspection.getCreatedAt(),
                inspection.getUpdatedAt(),
                inspection.getItemSnapshots().stream()
                        .sorted(Comparator.comparingInt(InspectionItemSnapshot::getSectionOrder)
                                .thenComparingInt(InspectionItemSnapshot::getItemOrder))
                        .map(this::toSnapshot)
                        .toList()
        );
    }

    private InspectionItemSnapshotResponse toSnapshot(InspectionItemSnapshot snapshot) {
        return new InspectionItemSnapshotResponse(
                snapshot.getId(),
                snapshot.getSourceTemplateItemId(),
                snapshot.getSectionTitle(),
                snapshot.getSectionDescription(),
                snapshot.getSectionOrder(),
                snapshot.getItemCode(),
                snapshot.getItemTitle(),
                snapshot.getItemDescription(),
                snapshot.getResponseType(),
                snapshot.isRequired(),
                snapshot.getRulesJson(),
                snapshot.getOptionsJson(),
                snapshot.getItemOrder(),
                snapshot.getCreatedAt()
        );
    }
}
