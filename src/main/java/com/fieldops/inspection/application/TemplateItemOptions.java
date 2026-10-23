package com.fieldops.inspection.application;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Converts SINGLE_CHOICE options to and from the {@code options_json} column,
 * stored as a JSON array of labels.
 */
public final class TemplateItemOptions {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };

    private TemplateItemOptions() {
    }

    public static String toJson(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        return JSON.writeValueAsString(options);
    }

    public static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.copyOf(JSON.readValue(json, LIST_OF_STRINGS));
    }
}
