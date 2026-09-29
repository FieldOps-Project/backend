package com.fieldops.inspection.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateItemTest {

    private TemplateSection sampleSection() {
        InspectionTemplate template = InspectionTemplate.create("T", "D", "C", UUID.randomUUID());
        InspectionTemplateVersion version = InspectionTemplateVersion.createDraft(template, "Snap", "Desc");
        return TemplateSection.create(version, "Secao", "Desc", 1);
    }

    @Test
    void createItem_setsAllFieldsCorrectly() {
        TemplateSection section = sampleSection();

        TemplateItem item = TemplateItem.create(
                section,
                "EXT-001",
                "Extintor dentro da validade?",
                "Verificar selo do Inmetro",
                ResponseType.CONFORMITY,
                true,
                true,
                true,
                null,
                1
        );

        assertThat(item.getSection()).isSameAs(section);
        assertThat(item.getCode()).isEqualTo("EXT-001");
        assertThat(item.getTitle()).isEqualTo("Extintor dentro da validade?");
        assertThat(item.getDescription()).isEqualTo("Verificar selo do Inmetro");
        assertThat(item.getResponseType()).isEqualTo(ResponseType.CONFORMITY);
        assertThat(item.isRequired()).isTrue();
        assertThat(item.isObservationRequiredOnFailure()).isTrue();
        assertThat(item.isEvidenceRequiredOnFailure()).isTrue();
        assertThat(item.getOptionsJson()).isNull();
        assertThat(item.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void createItem_withSingleChoiceOptions() {
        TemplateSection section = sampleSection();
        String options = "[\"Bom\", \"Regular\", \"Ruim\"]";

        TemplateItem item = TemplateItem.create(
                section, "COND-001", "Estado geral", null,
                ResponseType.SINGLE_CHOICE, true, false, false, options, 2
        );

        assertThat(item.getResponseType()).isEqualTo(ResponseType.SINGLE_CHOICE);
        assertThat(item.getOptionsJson()).isEqualTo(options);
    }

    @Test
    void createItem_blankTitle_throwsException() {
        TemplateSection section = sampleSection();

        assertThatThrownBy(() ->
                TemplateItem.create(section, null, "  ", null,
                        ResponseType.BOOLEAN, true, false, false, null, 1)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("title");
    }

    @Test
    void createItem_nullResponseType_throwsException() {
        TemplateSection section = sampleSection();

        assertThatThrownBy(() ->
                TemplateItem.create(section, null, "Titulo", null,
                        null, true, false, false, null, 1)
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("responseType");
    }

    @Test
    void createItem_nullSection_throwsException() {
        assertThatThrownBy(() ->
                TemplateItem.create(null, null, "Titulo", null,
                        ResponseType.TEXT_SHORT, true, false, false, null, 1)
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("section");
    }

    @Test
    void updateMetadata_changesFields() {
        TemplateSection section = sampleSection();
        TemplateItem item = TemplateItem.create(
                section, "OLD", "Titulo antigo", "Desc antiga",
                ResponseType.BOOLEAN, true, false, false, null, 1
        );

        item.updateMetadata("NEW", "Titulo novo", "Desc nova",
                ResponseType.NUMBER, false, true, true, null);

        assertThat(item.getCode()).isEqualTo("NEW");
        assertThat(item.getTitle()).isEqualTo("Titulo novo");
        assertThat(item.getDescription()).isEqualTo("Desc nova");
        assertThat(item.getResponseType()).isEqualTo(ResponseType.NUMBER);
        assertThat(item.isRequired()).isFalse();
        assertThat(item.isObservationRequiredOnFailure()).isTrue();
        assertThat(item.isEvidenceRequiredOnFailure()).isTrue();
    }

    @Test
    void allSevenResponseTypes_exist() {
        assertThat(ResponseType.values()).hasSize(7);
        assertThat(ResponseType.values()).containsExactly(
                ResponseType.TEXT_SHORT,
                ResponseType.TEXT_LONG,
                ResponseType.NUMBER,
                ResponseType.BOOLEAN,
                ResponseType.CONFORMITY,
                ResponseType.SINGLE_CHOICE,
                ResponseType.DATE
        );
    }
}
