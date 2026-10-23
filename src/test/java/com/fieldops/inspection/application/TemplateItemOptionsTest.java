package com.fieldops.inspection.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateItemOptionsTest {

    @Test
    void roundTripsOptionsAsJsonArray() {
        String json = TemplateItemOptions.toJson(List.of("Bom", "Regular", "Ruim"));

        assertThat(json).isEqualTo("[\"Bom\",\"Regular\",\"Ruim\"]");
        assertThat(TemplateItemOptions.fromJson(json)).containsExactly("Bom", "Regular", "Ruim");
    }

    @Test
    void storesNoJsonWhenThereAreNoOptions() {
        assertThat(TemplateItemOptions.toJson(List.of())).isNull();
        assertThat(TemplateItemOptions.toJson(null)).isNull();
    }

    @Test
    void readsMissingJsonAsEmptyList() {
        assertThat(TemplateItemOptions.fromJson(null)).isEmpty();
        assertThat(TemplateItemOptions.fromJson(" ")).isEmpty();
    }
}
