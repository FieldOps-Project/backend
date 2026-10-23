package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateItemRulesTest {

    private static TemplateItemCommand command(
            ResponseType type,
            Boolean observationOnFailure,
            Boolean evidenceOnFailure,
            List<String> options
    ) {
        return new TemplateItemCommand(null, "Item", null, type, null, observationOnFailure, evidenceOnFailure, options);
    }

    @Test
    void rejectsItemWithoutResponseType() {
        assertThatThrownBy(() -> TemplateItemRules.validate(command(null, null, null, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ITEM_RESPONSE_TYPE_REQUIRED");
    }

    @Test
    void acceptsSingleChoiceWithTwoOptionsAndTrimsThem() {
        List<String> options = TemplateItemRules.validate(
                command(ResponseType.SINGLE_CHOICE, null, null, List.of(" Bom ", "Ruim"))
        );

        assertThat(options).containsExactly("Bom", "Ruim");
    }

    @Test
    void rejectsSingleChoiceWithLessThanTwoOptions() {
        assertThatThrownBy(() -> TemplateItemRules.validate(
                command(ResponseType.SINGLE_CHOICE, null, null, List.of("Bom"))
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ITEM_OPTIONS_REQUIRED");
    }

    @Test
    void rejectsSingleChoiceWithoutOptions() {
        assertThatThrownBy(() -> TemplateItemRules.validate(command(ResponseType.SINGLE_CHOICE, null, null, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ITEM_OPTIONS_REQUIRED");
    }

    @Test
    void rejectsSingleChoiceWithBlankOption() {
        assertThatThrownBy(() -> TemplateItemRules.validate(
                command(ResponseType.SINGLE_CHOICE, null, null, List.of("Bom", "  "))
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ITEM_OPTIONS_REQUIRED");
    }

    @Test
    void rejectsSingleChoiceWithDuplicatedOptionsIgnoringCase() {
        assertThatThrownBy(() -> TemplateItemRules.validate(
                command(ResponseType.SINGLE_CHOICE, null, null, List.of("Bom", " bom"))
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ITEM_OPTIONS_DUPLICATED");
    }

    @ParameterizedTest
    @EnumSource(value = ResponseType.class, names = "SINGLE_CHOICE", mode = EnumSource.Mode.EXCLUDE)
    void rejectsOptionsForTypesThatDoNotUseThem(ResponseType type) {
        assertThatThrownBy(() -> TemplateItemRules.validate(command(type, null, null, List.of("A", "B"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ITEM_OPTIONS_NOT_ALLOWED");
    }

    @Test
    void treatsEmptyOptionsAsAbsentForOtherTypes() {
        assertThat(TemplateItemRules.validate(command(ResponseType.TEXT_SHORT, null, null, List.of()))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ResponseType.class, names = {"CONFORMITY", "BOOLEAN"})
    void acceptsFailureRulesForTypesWithFailure(ResponseType type) {
        assertThat(TemplateItemRules.validate(command(type, true, true, null))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ResponseType.class, names = {"CONFORMITY", "BOOLEAN"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectsEvidenceOnFailureForTypesWithoutFailure(ResponseType type) {
        List<String> options = type == ResponseType.SINGLE_CHOICE ? List.of("A", "B") : null;

        assertThatThrownBy(() -> TemplateItemRules.validate(command(type, false, true, options)))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ITEM_FAILURE_RULES_NOT_ALLOWED");
    }

    @Test
    void rejectsObservationOnFailureForDateItems() {
        assertThatThrownBy(() -> TemplateItemRules.validate(command(ResponseType.DATE, true, false, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ITEM_FAILURE_RULES_NOT_ALLOWED");
    }
}
