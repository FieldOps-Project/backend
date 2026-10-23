package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.shared.domain.exception.InvalidRequestException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Coherence rules between the response type of an item and its options and
 * failure requirements (RN-016).
 */
public final class TemplateItemRules {

    public static final int MIN_CHOICE_OPTIONS = 2;

    private static final Set<ResponseType> TYPES_WITH_FAILURE = Set.of(ResponseType.CONFORMITY, ResponseType.BOOLEAN);

    private TemplateItemRules() {
    }

    /**
     * Validates the command and returns its options trimmed, or an empty list
     * when the response type does not use options.
     */
    public static List<String> validate(TemplateItemCommand command) {
        ResponseType responseType = command.responseType();
        if (responseType == null) {
            throw new InvalidRequestException(
                    "TEMPLATE_ITEM_RESPONSE_TYPE_REQUIRED",
                    "Every item must declare a response type"
            );
        }

        boolean failureRules = command.isObservationRequiredOnFailure() || command.isEvidenceRequiredOnFailure();
        if (failureRules && !TYPES_WITH_FAILURE.contains(responseType)) {
            throw new InvalidRequestException(
                    "TEMPLATE_ITEM_FAILURE_RULES_NOT_ALLOWED",
                    "Observation or evidence on failure only applies to CONFORMITY and BOOLEAN items"
            );
        }

        List<String> options = command.options() == null
                ? List.of()
                : command.options().stream().map(option -> option == null ? "" : option.trim()).toList();

        if (responseType != ResponseType.SINGLE_CHOICE) {
            if (!options.isEmpty()) {
                throw new InvalidRequestException(
                        "TEMPLATE_ITEM_OPTIONS_NOT_ALLOWED",
                        "Options are only accepted for SINGLE_CHOICE items"
                );
            }
            return List.of();
        }

        if (options.size() < MIN_CHOICE_OPTIONS || options.stream().anyMatch(String::isEmpty)) {
            throw new InvalidRequestException(
                    "TEMPLATE_ITEM_OPTIONS_REQUIRED",
                    "SINGLE_CHOICE items require at least two non-blank options"
            );
        }

        Set<String> seen = new HashSet<>();
        for (String option : options) {
            if (!seen.add(option.toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException(
                        "TEMPLATE_ITEM_OPTIONS_DUPLICATED",
                        "SINGLE_CHOICE options must be unique"
                );
            }
        }
        return options;
    }
}
