package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.ResponseType;

import java.util.List;

/**
 * Item data shared by the create and update use cases of the draft editor.
 */
public record TemplateItemCommand(
        String code,
        String title,
        String description,
        ResponseType responseType,
        Boolean required,
        Boolean observationRequiredOnFailure,
        Boolean evidenceRequiredOnFailure,
        List<String> options
) {

    public boolean isRequired() {
        return required == null || required;
    }

    public boolean isObservationRequiredOnFailure() {
        return Boolean.TRUE.equals(observationRequiredOnFailure);
    }

    public boolean isEvidenceRequiredOnFailure() {
        return Boolean.TRUE.equals(evidenceRequiredOnFailure);
    }
}
