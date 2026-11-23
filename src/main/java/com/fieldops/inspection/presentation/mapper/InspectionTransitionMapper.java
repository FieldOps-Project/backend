package com.fieldops.inspection.presentation.mapper;

import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.FieldPosition;
import com.fieldops.inspection.presentation.dto.InspectionExecutionResponse;
import com.fieldops.inspection.presentation.dto.InspectionPositionResponse;
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

    public InspectionExecutionResponse toExecutionResponse(
            Inspection inspection,
            InspectionStatus previousStatus
    ) {
        return new InspectionExecutionResponse(
                inspection.getId(),
                previousStatus,
                inspection.getStatus(),
                inspection.getTechnicianId(),
                inspection.getStartedAtDevice(),
                inspection.getStartedAtServer(),
                inspection.getCompletedAtDevice(),
                inspection.getSubmittedAtServer(),
                toPositionResponse(inspection.getStartPosition()),
                toPositionResponse(inspection.getCompletionPosition())
        );
    }

    private InspectionPositionResponse toPositionResponse(FieldPosition position) {
        return position == null ? null : new InspectionPositionResponse(
                position.getLatitude(),
                position.getLongitude(),
                position.getAccuracyMeters(),
                position.getCapturedAtDevice()
        );
    }
}
