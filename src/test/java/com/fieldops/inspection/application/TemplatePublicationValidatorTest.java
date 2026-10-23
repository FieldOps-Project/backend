package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.shared.domain.exception.Violation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemplatePublicationValidatorTest {

    private final InspectionTemplate template =
            InspectionTemplate.create("Extintores", null, "Incendio", UUID.randomUUID());
    private final InspectionTemplateVersion draft =
            InspectionTemplateVersion.createDraft(template, "Extintores", null);

    @Test
    void validDraftHasNoViolations() {
        TemplateSection section = section("Condicoes", 1);
        item(section, ResponseType.CONFORMITY, true, null, 1);
        item(section, ResponseType.SINGLE_CHOICE, false, "[\"Sim\",\"Nao\"]", 2);

        assertThat(TemplatePublicationValidator.validate(draft)).isEmpty();
    }

    @Test
    void draftWithoutSectionsIsRejected() {
        assertThat(fields(TemplatePublicationValidator.validate(draft))).containsExactly("sections");
    }

    @Test
    void collectsEveryPendingIssueWithItsFieldPath() {
        ReflectionTestUtils.setField(template, "category", " ");
        TemplateSection first = section("Condicoes", 1);
        item(first, ResponseType.TEXT_SHORT, false, null, 1);
        item(first, ResponseType.SINGLE_CHOICE, false, "[\"Sim\"]", 2);
        ReflectionTestUtils.setField(item(first, ResponseType.TEXT_LONG, false, null, 3), "responseType", null);
        item(first, ResponseType.NUMBER, true, null, 4);
        section("Sinalizacao", 2);

        List<Violation> violations = TemplatePublicationValidator.validate(draft);

        assertThat(fields(violations)).containsExactly(
                "category",
                "sections[0].items[1].options",
                "sections[0].items[2].responseType",
                "sections[0].items[3].evidenceRequiredOnFailure",
                "sections[1].items"
        );
        assertThat(violations.get(4).message()).contains("Sinalizacao");
    }

    @Test
    void reportsDuplicatedDisplayOrder() {
        TemplateSection section = section("Condicoes", 1);
        item(section, ResponseType.TEXT_SHORT, false, null, 1);
        item(section, ResponseType.TEXT_LONG, false, null, 1);
        TemplateSection repeated = section("Repetida", 1);
        item(repeated, ResponseType.DATE, false, null, 1);

        assertThat(fields(TemplatePublicationValidator.validate(draft))).containsExactlyInAnyOrder(
                "sections[0].items[1].displayOrder",
                "sections[1].displayOrder"
        );
    }

    @Test
    void everyPersistedResponseTypeIsSupportedByTheMobileApp() {
        assertThat(TemplatePublicationValidator.MVP_RESPONSE_TYPES)
                .isEqualTo(EnumSet.allOf(ResponseType.class));
    }

    private TemplateSection section(String title, int displayOrder) {
        TemplateSection section = TemplateSection.create(draft, title, null, displayOrder);
        draft.addSection(section);
        return section;
    }

    private TemplateItem item(TemplateSection section, ResponseType type, boolean evidenceOnFailure, String options, int order) {
        TemplateItem item = TemplateItem.create(
                section, null, "Item " + order, null, type, true, false, evidenceOnFailure, options, order
        );
        section.addItem(item);
        return item;
    }

    private static List<String> fields(List<Violation> violations) {
        return violations.stream().map(Violation::field).toList();
    }
}
