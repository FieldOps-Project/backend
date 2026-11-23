package com.fieldops.inspection.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fieldops.inspection.application.InspectionResponseValidator;
import com.fieldops.inspection.presentation.dto.SaveInspectionResponseRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionResponseValidatorTest {

    private InspectionResponseValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InspectionResponseValidator();
    }

    @ParameterizedTest
    @EnumSource(ResponseType.class)
    void acceptsTheValueFieldDefinedByEachSnapshotType(ResponseType responseType) {
        InspectionItemSnapshot snapshot = snapshot(
                responseType,
                responseType == ResponseType.SINGLE_CHOICE ? "[\"Bom\"]" : null,
                "{}"
        );

        assertThat(validator.validate(snapshot, validRequest(responseType))).isNotNull();
    }

    @Test
    void rejectsAValueFieldThatDoesNotMatchTheSnapshotType() {
        InspectionItemSnapshot snapshot = snapshot(ResponseType.NUMBER, null, "{}");

        assertThatThrownBy(() -> validator.validate(snapshot, request(
                "texto", null, null, null, null, null, null
        )))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The inspection response is invalid");
    }

    @Test
    void validatesNumberRangeFromTheSnapshotRules() {
        InspectionItemSnapshot snapshot = snapshot(ResponseType.NUMBER, null, "{\"min\": 1, \"max\": 5}");

        assertThatThrownBy(() -> validator.validate(snapshot, request(
                null, BigDecimal.valueOf(6), null, null, null, null, null
        )))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void validatesSingleChoiceAgainstSnapshotOptions() {
        InspectionItemSnapshot snapshot = snapshot(ResponseType.SINGLE_CHOICE, "[\"Bom\", \"Ruim\"]", "{}");

        assertThatThrownBy(() -> validator.validate(snapshot, request(
                "Regular", null, null, null, null, null, null
        )))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(validator.validate(snapshot, request(
                "Bom", null, null, null, null, null, null
        ))).isNotNull();
    }

    private SaveInspectionResponseRequest validRequest(ResponseType responseType) {
        return switch (responseType) {
            case TEXT_SHORT, TEXT_LONG -> request("texto", null, null, null, null, null, null);
            case NUMBER -> request(null, BigDecimal.ONE, null, null, null, null, null);
            case BOOLEAN -> request(null, null, true, null, null, null, null);
            case CONFORMITY -> request(null, null, null, null, null, null,
                    InspectionResponseConformity.CONFORMING);
            case SINGLE_CHOICE -> request("Bom", null, null, null, null, null, null);
            case DATE -> request(null, null, null, LocalDate.of(2026, 8, 1), null, null, null);
        };
    }

    private InspectionItemSnapshot snapshot(ResponseType type, String options, String rules) {
        InspectionItemSnapshot snapshot = new InspectionItemSnapshot();
        ReflectionTestUtils.setField(snapshot, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(snapshot, "responseType", type);
        ReflectionTestUtils.setField(snapshot, "optionsJson", options);
        ReflectionTestUtils.setField(snapshot, "rulesJson", rules);
        return snapshot;
    }

    private SaveInspectionResponseRequest request(
            String valueText,
            BigDecimal valueNumber,
            Boolean valueBoolean,
            LocalDate valueDate,
            JsonNode valueJson,
            String observation,
            InspectionResponseConformity conformity
    ) {
        return new SaveInspectionResponseRequest(
                UUID.randomUUID(), UUID.randomUUID(), valueText, valueNumber, valueBoolean, valueDate,
                valueJson, conformity, observation, Instant.parse("2026-08-01T13:12:30Z"), 0
        );
    }
}
