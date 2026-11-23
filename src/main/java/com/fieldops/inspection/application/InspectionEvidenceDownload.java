package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.InspectionEvidence;

public record InspectionEvidenceDownload(InspectionEvidence evidence, byte[] content) {
}
