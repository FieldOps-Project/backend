package com.fieldops.inspection.domain.model;

import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
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

    @Test
    void removeSection_removesSectionFromList() {
        InspectionTemplateVersion version = InspectionTemplateVersion.createDraft(sampleTemplate(), "T", "D");
        TemplateSection section = TemplateSection.create(version, "Secao 1", null, 1);
        version.addSection(section);

        version.removeSection(section);

        assertThat(version.getSections()).isEmpty();
    }

    @Test
    void ensureEditable_acceptsDraft() {
        InspectionTemplateVersion draft = InspectionTemplateVersion.createDraft(sampleTemplate(), "T", "D");

        draft.ensureEditable();
    }

    @Test
    void ensureEditable_rejectsPublishedVersionWithConflict() {
        InspectionTemplateVersion published = InspectionTemplateVersion.createPublished(
                sampleTemplate(), 1, "T", "D", UUID.randomUUID()
        );

        assertThatThrownBy(published::ensureEditable)
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_VERSION_PUBLISHED");
    }

    @Test
    void updateSnapshot_changesDraftTitleAndDescription() {
        InspectionTemplateVersion draft = InspectionTemplateVersion.createDraft(sampleTemplate(), "Old", "Old desc");

        draft.updateSnapshot(" New ", "  ");

        assertThat(draft.getTitleSnapshot()).isEqualTo("New");
        assertThat(draft.getDescriptionSnapshot()).isNull();
    }

    @Test
    void updateSnapshot_rejectsPublishedVersion() {
        InspectionTemplateVersion published = InspectionTemplateVersion.createPublished(
                sampleTemplate(), 1, "T", "D", UUID.randomUUID()
        );

        assertThatThrownBy(() -> published.updateSnapshot("New", null))
                .isInstanceOf(ConflictException.class);
        assertThat(published.getTitleSnapshot()).isEqualTo("T");
    }

    @Test
    void publishCopyOf_copiesDraftStructureIntoIndependentVersion() {
        InspectionTemplate template = sampleTemplate();
        InspectionTemplateVersion draft = InspectionTemplateVersion.createDraft(template, "Template", "Desc");
        TemplateSection section = TemplateSection.create(draft, "Secao 1", "Desc secao", 1);
        section.addItem(TemplateItem.create(
                section, "EXT-01", "Lacre", null, ResponseType.SINGLE_CHOICE,
                true, false, false, "[\"Sim\",\"Nao\"]", 1
        ));
        draft.addSection(section);
        UUID publishedBy = UUID.randomUUID();

        InspectionTemplateVersion published = InspectionTemplateVersion.publishCopyOf(draft, 3, publishedBy);

        assertThat(published.getVersionNumber()).isEqualTo(3);
        assertThat(published.getPublishedBy()).isEqualTo(publishedBy);
        assertThat(published.isActiveForNewInspections()).isTrue();
        assertThat(published.getTitleSnapshot()).isEqualTo("Template");
        TemplateSection copiedSection = published.getSections().get(0);
        assertThat(copiedSection).isNotSameAs(section);
        assertThat(copiedSection.getTemplateVersion()).isSameAs(published);
        assertThat(copiedSection.getTitle()).isEqualTo("Secao 1");
        TemplateItem copiedItem = copiedSection.getItems().get(0);
        assertThat(copiedItem.getSection()).isSameAs(copiedSection);
        assertThat(copiedItem.getCode()).isEqualTo("EXT-01");
        assertThat(copiedItem.getOptionsJson()).isEqualTo("[\"Sim\",\"Nao\"]");

        section.updateMetadata("Secao editada", null);
        draft.removeSection(section);

        assertThat(draft.isDraft()).isTrue();
        assertThat(published.getSections()).hasSize(1);
        assertThat(copiedSection.getTitle()).isEqualTo("Secao 1");
    }

    @Test
    void publishCopyOf_rejectsPublishedSource() {
        InspectionTemplateVersion published = InspectionTemplateVersion.createPublished(
                sampleTemplate(), 1, "T", "D", UUID.randomUUID()
        );

        assertThatThrownBy(() -> InspectionTemplateVersion.publishCopyOf(published, 2, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateAvailability_togglesPublishedVersion() {
        InspectionTemplateVersion published = InspectionTemplateVersion.createPublished(
                sampleTemplate(), 1, "T", "D", UUID.randomUUID()
        );

        published.updateAvailability(false);
        assertThat(published.isActiveForNewInspections()).isFalse();

        published.updateAvailability(true);
        assertThat(published.isActiveForNewInspections()).isTrue();
    }

    @Test
    void updateAvailability_rejectsDraft() {
        InspectionTemplateVersion draft = InspectionTemplateVersion.createDraft(sampleTemplate(), "T", "D");

        assertThatThrownBy(() -> draft.updateAvailability(true))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_VERSION_NOT_PUBLISHED");
        assertThat(draft.isActiveForNewInspections()).isFalse();
    }
}
