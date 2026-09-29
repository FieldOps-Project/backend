package com.fieldops.inspection.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateSectionTest {

    private InspectionTemplateVersion sampleVersion() {
        InspectionTemplate template = InspectionTemplate.create("T", "D", "C", UUID.randomUUID());
        return InspectionTemplateVersion.createDraft(template, "Snapshot", "Desc");
    }

    @Test
    void createSection_setsFieldsCorrectly() {
        InspectionTemplateVersion version = sampleVersion();

        TemplateSection section = TemplateSection.create(version, "Seguranca Eletrica", "Itens eletricos", 1);

        assertThat(section.getTemplateVersion()).isSameAs(version);
        assertThat(section.getTitle()).isEqualTo("Seguranca Eletrica");
        assertThat(section.getDescription()).isEqualTo("Itens eletricos");
        assertThat(section.getDisplayOrder()).isEqualTo(1);
        assertThat(section.getItems()).isEmpty();
    }

    @Test
    void createSection_blankTitle_throwsException() {
        InspectionTemplateVersion version = sampleVersion();

        assertThatThrownBy(() ->
                TemplateSection.create(version, "", "desc", 1)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("title");
    }

    @Test
    void createSection_nullVersion_throwsException() {
        assertThatThrownBy(() ->
                TemplateSection.create(null, "Title", "desc", 1)
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("templateVersion");
    }

    @Test
    void updateMetadata_changesFields() {
        InspectionTemplateVersion version = sampleVersion();
        TemplateSection section = TemplateSection.create(version, "Original", "Desc original", 1);

        section.updateMetadata("Atualizado", "Nova desc");

        assertThat(section.getTitle()).isEqualTo("Atualizado");
        assertThat(section.getDescription()).isEqualTo("Nova desc");
    }

    @Test
    void updateDisplayOrder_changesOrder() {
        InspectionTemplateVersion version = sampleVersion();
        TemplateSection section = TemplateSection.create(version, "Title", "Desc", 1);

        section.updateDisplayOrder(5);

        assertThat(section.getDisplayOrder()).isEqualTo(5);
    }

    @Test
    void addItem_addsItemToList() {
        InspectionTemplateVersion version = sampleVersion();
        TemplateSection section = TemplateSection.create(version, "Secao", "Desc", 1);

        TemplateItem item = TemplateItem.create(
                section, "EXT-001", "Extintor carregado?", null,
                ResponseType.CONFORMITY, true, true, false, null, 1
        );
        section.addItem(item);

        assertThat(section.getItems()).hasSize(1);
        assertThat(section.getItems().get(0).getCode()).isEqualTo("EXT-001");
    }
}
