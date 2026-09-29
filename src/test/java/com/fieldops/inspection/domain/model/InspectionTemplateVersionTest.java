package com.fieldops.inspection.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionTemplateVersionTest {

    private InspectionTemplate sampleTemplate() {
        return InspectionTemplate.create("Template", "Desc", "Cat", UUID.randomUUID());
    }

    @Test
    void createDraft_setsCorrectDefaults() {
        InspectionTemplate template = sampleTemplate();

        InspectionTemplateVersion draft = InspectionTemplateVersion.createDraft(
                template, "Titulo Snapshot", "Desc Snapshot"
        );

        assertThat(draft.getTemplate()).isSameAs(template);
        assertThat(draft.getVersionNumber()).isZero();
        assertThat(draft.getTitleSnapshot()).isEqualTo("Titulo Snapshot");
        assertThat(draft.getDescriptionSnapshot()).isEqualTo("Desc Snapshot");
        assertThat(draft.getPublishedBy()).isNull();
        assertThat(draft.getPublishedAt()).isNull();
        assertThat(draft.isActiveForNewInspections()).isFalse();
        assertThat(draft.isDraft()).isTrue();
    }

    @Test
    void createPublished_setsVersionAndTimestamp() {
        InspectionTemplate template = sampleTemplate();
        UUID publishedBy = UUID.randomUUID();

        InspectionTemplateVersion published = InspectionTemplateVersion.createPublished(
                template, 1, "Titulo v1", "Desc v1", publishedBy
        );

        assertThat(published.getVersionNumber()).isEqualTo(1);
        assertThat(published.getPublishedBy()).isEqualTo(publishedBy);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.isActiveForNewInspections()).isTrue();
        assertThat(published.isDraft()).isFalse();
    }

    @Test
    void createPublished_versionZero_throwsException() {
        InspectionTemplate template = sampleTemplate();

        assertThatThrownBy(() ->
                InspectionTemplateVersion.createPublished(template, 0, "T", "D", UUID.randomUUID())
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("version number must be >= 1");
    }

    @Test
    void createDraft_blankTitle_throwsException() {
        InspectionTemplate template = sampleTemplate();

        assertThatThrownBy(() ->
                InspectionTemplateVersion.createDraft(template, "  ", "desc")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("titleSnapshot");
    }

    @Test
    void addSection_addsSectionToList() {
        InspectionTemplate template = sampleTemplate();
        InspectionTemplateVersion version = InspectionTemplateVersion.createDraft(template, "T", "D");

        TemplateSection section = TemplateSection.create(version, "Secao 1", "Desc", 1);
        version.addSection(section);

        assertThat(version.getSections()).hasSize(1);
        assertThat(version.getSections().get(0).getTitle()).isEqualTo("Secao 1");
    }
}
