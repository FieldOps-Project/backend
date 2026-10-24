package com.fieldops.inspection.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionTest {

    private InspectionTemplate template;
    private InspectionTemplateVersion published;
    private final Instant scheduledFor = Instant.parse("2026-11-03T13:00:00Z");

    @BeforeEach
    void setUp() {
        template = InspectionTemplate.create("Extintores", null, "Incendio", UUID.randomUUID());
        published = InspectionTemplateVersion.createPublished(template, 1, "Extintores", null, UUID.randomUUID());
        ReflectionTestUtils.setField(published, "id", UUID.randomUUID());

        TemplateSection second = TemplateSection.create(published, "Sinalizacao", null, 2);
        second.addItem(item(second, "Placa visivel?", ResponseType.BOOLEAN, false, null, 1));
        TemplateSection first = TemplateSection.create(published, "Condicoes", "Visual", 1);
        first.addItem(item(first, "Pintura", ResponseType.SINGLE_CHOICE, false, "[\"Bom\",\"Ruim\"]", 2));
        first.addItem(item(first, "Lacre intacto?", ResponseType.CONFORMITY, true, null, 1));
        published.addSection(second);
        published.addSection(first);
    }

    @Test
    void createAssignsInspectionAndFreezesItemsInChecklistOrder() {
        UUID technician = UUID.randomUUID();

        Inspection inspection = Inspection.create(
                published, UUID.randomUUID(), UUID.randomUUID(), null, technician, null, UUID.randomUUID(),
                " Extintores - Bloco A ", "  ", null, scheduledFor
        );

        assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.ASSIGNED);
        assertThat(inspection.getPriority()).isEqualTo(InspectionPriority.MEDIUM);
        assertThat(inspection.getTemplateVersionId()).isEqualTo(published.getId());
        assertThat(inspection.getTechnicianId()).isEqualTo(technician);
        assertThat(inspection.getTitle()).isEqualTo("Extintores - Bloco A");
        assertThat(inspection.getInstructions()).isNull();
        assertThat(inspection.getStartedAtDevice()).isNull();
        assertThat(inspection.getStartedAtServer()).isNull();

        List<InspectionItemSnapshot> snapshots = inspection.getItemSnapshots();
        assertThat(snapshots).extracting(InspectionItemSnapshot::getItemTitle)
                .containsExactly("Lacre intacto?", "Pintura", "Placa visivel?");
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.getInspection()).isSameAs(inspection));
        assertThat(snapshots.get(0).getSectionTitle()).isEqualTo("Condicoes");
        assertThat(snapshots.get(0).getSectionDescription()).isEqualTo("Visual");
        assertThat(snapshots.get(0).getRulesJson())
                .isEqualTo("{\"observationRequiredOnFailure\":false,\"evidenceRequiredOnFailure\":true}");
        assertThat(snapshots.get(1).getOptionsJson()).isEqualTo("[\"Bom\",\"Ruim\"]");
        assertThat(snapshots.get(2).getSectionOrder()).isEqualTo(2);
    }

    @Test
    void snapshotsKeepTheTextWhenTheTemplateItemChangesLater() {
        Inspection inspection = Inspection.create(
                published, UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), null, UUID.randomUUID(),
                "Extintores", null, InspectionPriority.HIGH, scheduledFor
        );
        TemplateItem original = published.getSections().get(1).getItems().get(1);

        original.updateMetadata(
                original.getCode(), "Texto reescrito", null, original.getResponseType(),
                true, false, true, null
        );

        assertThat(inspection.getItemSnapshots().get(0).getItemTitle()).isEqualTo("Lacre intacto?");
    }

    @Test
    void createRejectsDraftVersion() {
        InspectionTemplateVersion draft = InspectionTemplateVersion.createDraft(template, "Extintores", null);

        assertThatThrownBy(() -> Inspection.create(
                draft, UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), null, UUID.randomUUID(),
                "Extintores", null, null, scheduledFor
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRequiresClientSiteTechnicianAndSchedule() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> Inspection.create(published, null, id, null, id, null, id, "T", null, null, scheduledFor))
                .hasMessageContaining("clientId");
        assertThatThrownBy(() -> Inspection.create(published, id, null, null, id, null, id, "T", null, null, scheduledFor))
                .hasMessageContaining("siteId");
        assertThatThrownBy(() -> Inspection.create(published, id, id, null, null, null, id, "T", null, null, scheduledFor))
                .hasMessageContaining("technicianId");
        assertThatThrownBy(() -> Inspection.create(published, id, id, null, id, null, id, "T", null, null, null))
                .hasMessageContaining("scheduledFor");
    }

    private static TemplateItem item(
            TemplateSection section, String title, ResponseType type, boolean evidence, String options, int order
    ) {
        return TemplateItem.create(section, null, title, null, type, true, false, evidence, options, order);
    }
}
