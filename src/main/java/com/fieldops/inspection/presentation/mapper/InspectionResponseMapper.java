package com.fieldops.inspection.presentation.mapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.inspection.presentation.dto.InspectionResponseResponse;
import org.springframework.stereotype.Component;

@Component
public class InspectionResponseMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public InspectionResponseResponse toResponse(InspectionResponse response) {
        return new InspectionResponseResponse(
                response.getId(),
                response.getInspectionItemId(),
                response.getValueText(),
                response.getValueNumber(),
                response.getValueBoolean(),
                response.getValueDate(),
                parseJson(response.getValueJson()),
                response.getConformity(),
                response.getObservation(),
                response.getAnsweredAtDevice(),
                response.getAnsweredAtServer(),
                response.getAnsweredBy(),
                response.getBaseVersion(),
                response.getVersion()
        );
    }

    private JsonNode parseJson(String valueJson) {
        if (valueJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(valueJson);
        } catch (Exception exception) {
            return null;
        }
    }
}
