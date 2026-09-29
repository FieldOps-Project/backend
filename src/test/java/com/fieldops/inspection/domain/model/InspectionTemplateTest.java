package com.fieldops.inspection.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionTemplateTest {

    @Test
    void createTemplate_setsFieldsCorrectly() {
        UUID createdBy = UUID.randomUUID();

        InspectionTemplate template = InspectionTemplate.create(
                "Checklist de Extintores",
                "Verificacao mensal de extintores",
                "Seguranca",
                createdBy
        );

        assertThat(template.getTitle()).isEqualTo("Checklist de Extintores");
        assertThat(template.getDescription()).isEqualTo("Verificacao mensal de extintores");
        assertThat(template.getCategory()).isEqualTo("Seguranca");
        assertThat(template.getCreatedBy()).isEqualTo(createdBy);
        assertThat(template.getStatus()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(template.getCurrentVersion()).isNull();
    }

    @Test
    void createTemplate_trimsWhitespace() {
        UUID createdBy = UUID.randomUUID();

        InspectionTemplate template = InspectionTemplate.create(
                "  Titulo com espacos  ",
                "  Descricao  ",
                "  Categoria  ",
                createdBy
        );

        assertThat(template.getTitle()).isEqualTo("Titulo com espacos");
        assertThat(template.getDescription()).isEqualTo("Descricao");
        assertThat(template.getCategory()).isEqualTo("Categoria");
    }

    @Test
    void createTemplate_nullDescription_becomesNull() {
        UUID createdBy = UUID.randomUUID();

        InspectionTemplate template = InspectionTemplate.create(
                "Titulo", null, "Categoria", createdBy
        );

        assertThat(template.getDescription()).isNull();
    }

    @Test
    void createTemplate_blankTitle_throwsException() {
        UUID createdBy = UUID.randomUUID();

        assertThatThrownBy(() ->
                InspectionTemplate.create("  ", "desc", "cat", createdBy)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("title");
    }

    @Test
    void createTemplate_nullCategory_throwsException() {
        UUID createdBy = UUID.randomUUID();

        assertThatThrownBy(() ->
                InspectionTemplate.create("Titulo", "desc", null, createdBy)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("category");
    }

    @Test
    void createTemplate_nullCreatedBy_throwsException() {
        assertThatThrownBy(() ->
                InspectionTemplate.create("Titulo", "desc", "cat", null)
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("createdBy");
    }

    @Test
    void updateMetadata_updatesFields() {
        UUID createdBy = UUID.randomUUID();
        InspectionTemplate template = InspectionTemplate.create(
                "Original", "Desc original", "Cat original", createdBy
        );

        template.updateMetadata("Novo Titulo", "Nova Desc", "Nova Cat");

        assertThat(template.getTitle()).isEqualTo("Novo Titulo");
        assertThat(template.getDescription()).isEqualTo("Nova Desc");
        assertThat(template.getCategory()).isEqualTo("Nova Cat");
    }

    @Test
    void updateStatus_changesStatus() {
        UUID createdBy = UUID.randomUUID();
        InspectionTemplate template = InspectionTemplate.create(
                "Titulo", "Desc", "Cat", createdBy
        );

        template.updateStatus(TemplateStatus.ACTIVE);

        assertThat(template.getStatus()).isEqualTo(TemplateStatus.ACTIVE);
    }

    @Test
    void advanceCurrentVersion_setsVersionNumber() {
        UUID createdBy = UUID.randomUUID();
        InspectionTemplate template = InspectionTemplate.create(
                "Titulo", "Desc", "Cat", createdBy
        );

        template.advanceCurrentVersion(3);

        assertThat(template.getCurrentVersion()).isEqualTo(3);
    }
}
