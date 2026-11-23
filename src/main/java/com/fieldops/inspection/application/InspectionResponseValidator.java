package com.fieldops.inspection.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionResponseValue;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.presentation.dto.SaveInspectionResponseRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.Violation;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class InspectionResponseValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public InspectionResponseValue validate(
            InspectionItemSnapshot snapshot,
            SaveInspectionResponseRequest request
    ) {
        validateObservation(request);
        return switch (snapshot.getResponseType()) {
            case TEXT_SHORT -> text(snapshot, request, 255);
            case TEXT_LONG -> text(snapshot, request, 2000);
            case NUMBER -> number(snapshot, request);
            case BOOLEAN -> bool(snapshot, request);
            case CONFORMITY -> conformity(snapshot, request);
            case SINGLE_CHOICE -> choice(snapshot, request);
            case DATE -> date(snapshot, request);
        };
    }

    private InspectionResponseValue text(InspectionItemSnapshot snapshot, SaveInspectionResponseRequest request, int max) {
        ensureOnly(request, Set.of("valueText"));
        if (request.valueText() == null || request.valueText().isBlank()) {
            invalid("valueText", "A text value is required for " + snapshot.getResponseType());
        }
        if (request.valueText().length() > max) {
            invalid("valueText", "The value exceeds the maximum length of " + max);
        }
        return new InspectionResponseValue(request.valueText(), null, null, null, null, null);
    }

    private InspectionResponseValue number(InspectionItemSnapshot snapshot, SaveInspectionResponseRequest request) {
        ensureOnly(request, Set.of("valueNumber"));
        if (request.valueNumber() == null) {
            invalid("valueNumber", "A numeric value is required");
        }
        JsonNode rules = rules(snapshot);
        checkRange(request.valueNumber(), rules, "min", "minimum");
        checkRange(request.valueNumber(), rules, "max", "maximum");
        return new InspectionResponseValue(null, request.valueNumber(), null, null, null, null);
    }

    private InspectionResponseValue bool(InspectionItemSnapshot snapshot, SaveInspectionResponseRequest request) {
        ensureOnly(request, Set.of("valueBoolean"));
        if (request.valueBoolean() == null) {
            invalid("valueBoolean", "A boolean value is required");
        }
        return new InspectionResponseValue(null, null, request.valueBoolean(), null, null, null);
    }

    private InspectionResponseValue conformity(InspectionItemSnapshot snapshot, SaveInspectionResponseRequest request) {
        ensureOnly(request, Set.of("conformity"));
        if (request.conformity() == null) {
            invalid("conformity", "A conformity value is required");
        }
        return new InspectionResponseValue(null, null, null, null, null, request.conformity());
    }

    private InspectionResponseValue choice(InspectionItemSnapshot snapshot, SaveInspectionResponseRequest request) {
        if ((request.valueText() == null) == (request.valueJson() == null)) {
            invalid("valueText", "Exactly one of valueText or valueJson is required");
        }
        ensureOnly(request, request.valueText() != null ? Set.of("valueText") : Set.of("valueJson"));
        JsonNode options = options(snapshot);
        JsonNode selected = request.valueText() != null
                ? objectMapper.valueToTree(request.valueText())
                : request.valueJson();
        boolean accepted = options != null && options.isArray();
        if (accepted) {
            accepted = false;
            for (JsonNode option : options) {
                if (option.equals(selected)) {
                    accepted = true;
                    break;
                }
            }
        }
        if (!accepted) {
            invalid(request.valueText() != null ? "valueText" : "valueJson",
                    "The selected value is not present in the snapshot options");
        }
        return new InspectionResponseValue(
                request.valueText(), null, null, null,
                request.valueJson() == null ? null : writeJson(request.valueJson()), null
        );
    }

    private InspectionResponseValue date(InspectionItemSnapshot snapshot, SaveInspectionResponseRequest request) {
        ensureOnly(request, Set.of("valueDate"));
        if (request.valueDate() == null) {
            invalid("valueDate", "A date value is required");
        }
        return new InspectionResponseValue(null, null, null, request.valueDate(), null, null);
    }

    private void validateObservation(SaveInspectionResponseRequest request) {
        if (request.observation() != null && request.observation().length() > 2000) {
            invalid("observation", "The observation exceeds the maximum length of 2000");
        }
    }

    private void ensureOnly(SaveInspectionResponseRequest request, Set<String> allowed) {
        List<String> supplied = new ArrayList<>();
        if (request.valueText() != null) supplied.add("valueText");
        if (request.valueNumber() != null) supplied.add("valueNumber");
        if (request.valueBoolean() != null) supplied.add("valueBoolean");
        if (request.valueDate() != null) supplied.add("valueDate");
        if (request.valueJson() != null) supplied.add("valueJson");
        if (request.conformity() != null) supplied.add("conformity");
        if (!allowed.containsAll(supplied) || !new HashSet<>(supplied).equals(new HashSet<>(allowed))) {
            invalid("value", "The supplied value field does not match the snapshot response type");
        }
    }

    private JsonNode rules(InspectionItemSnapshot snapshot) {
        return parseJson(snapshot.getRulesJson(), "rules_json");
    }

    private JsonNode options(InspectionItemSnapshot snapshot) {
        return snapshot.getOptionsJson() == null ? null : parseJson(snapshot.getOptionsJson(), "options_json");
    }

    private JsonNode parseJson(String value, String field) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("INSPECTION_SNAPSHOT_INVALID",
                    "The inspection snapshot contains invalid " + field,
                    List.of(new Violation(field, "The snapshot JSON is invalid")));
        }
    }

    private void checkRange(BigDecimal value, JsonNode rules, String primaryName, String alternateName) {
        if (value == null || rules == null) {
            return;
        }
        JsonNode limit = rules.get(primaryName);
        if (limit == null) {
            limit = rules.get(alternateName);
        }
        if (limit == null || !limit.isNumber()) {
            return;
        }
        BigDecimal boundary = limit.decimalValue();
        boolean invalid = "min".equals(primaryName) ? value.compareTo(boundary) < 0 : value.compareTo(boundary) > 0;
        if (invalid) {
            invalid("valueNumber", "The value is outside the allowed range");
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            invalid("valueJson", "The JSON value is invalid");
            return null;
        }
    }

    private void invalid(String field, String message) {
        throw new BusinessRuleException(
                "INSPECTION_RESPONSE_INVALID",
                "The inspection response is invalid",
                List.of(new Violation(field, message))
        );
    }
}
