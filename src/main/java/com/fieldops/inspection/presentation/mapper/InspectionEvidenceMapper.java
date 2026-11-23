package com.fieldops.inspection.presentation.mapper;

import com.fieldops.inspection.domain.model.InspectionEvidence;
import com.fieldops.inspection.presentation.dto.EvidenceResponse;
import org.springframework.stereotype.Component;

@Component
public class InspectionEvidenceMapper {

    public EvidenceResponse toResponse(InspectionEvidence evidence) {
        return new EvidenceResponse(
                evidence.getId(), evidence.getInspectionId(), evidence.getResponseId(), evidence.getNonConformityId(),
                evidence.getType(), evidence.getMimeType(), evidence.getSizeBytes(), evidence.getChecksum(),
                evidence.getDescription(), evidence.getLatitude(), evidence.getLongitude(), evidence.getAccuracyMeters(),
                evidence.getCapturedAtDevice(), evidence.getServerReceivedAt(), evidence.getUploadedAt(),
                evidence.getCreatedBy(), evidence.getDeletedAt()
        );
    }
}
