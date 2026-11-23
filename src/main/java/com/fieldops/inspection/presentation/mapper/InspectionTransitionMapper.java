package com.fieldops.inspection.presentation.mapper;

import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.presentation.dto.InspectionTransitionResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InspectionTransitionMapper {

    public InspectionTransitionResponse toResponse(
            Inspection inspection,
            InspectionStatus previousStatus
    ) {
        UUID id = inspection.getId();
        return new InspectionTransitionResponse(
                id,
                previousStatus,
                inspection.getStatus(),
                inspection.getTechnicianId(),
                inspection.getCanceledBy(),
                inspection.getCanceledAt(),
                inspection.getCanceledReason()
        );
    }
}
