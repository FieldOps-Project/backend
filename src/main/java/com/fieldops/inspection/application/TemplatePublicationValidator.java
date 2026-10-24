package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.Violation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects every pending issue that blocks the publication of a draft
 * (RN-015, RN-016, RN-023), so the builder can show them all in one cycle.
 */
public final class TemplatePublicationValidator {

    /** Response types the mobile app renders in the MVP (RN-023). */
    static final Set<ResponseType> MVP_RESPONSE_TYPES = EnumSet.of(
            ResponseType.TEXT_SHORT,
            ResponseType.TEXT_LONG,
            ResponseType.NUMBER,
            ResponseType.BOOLEAN,
            ResponseType.CONFORMITY,
            ResponseType.SINGLE_CHOICE,
            ResponseType.DATE
    );

    private TemplatePublicationValidator() {
    }

    public static List<Violation> validate(InspectionTemplateVersion draft) {
        List<Violation> violations = new ArrayList<>();
        InspectionTemplate template = draft.getTemplate();

        if (isBlank(template.getTitle())) {
            violations.add(new Violation("title", "Template title is required"));
        }
        if (isBlank(template.getCategory())) {
            violations.add(new Violation("category", "Template category is required"));
        }

        List<TemplateSection> sections = draft.getSections().stream()
                .sorted(Comparator.comparingInt(TemplateSection::getDisplayOrder))
                .toList();
        if (sections.isEmpty()) {
            violations.add(new Violation("sections", "Template must have at least one section"));
        }

        Set<Integer> sectionOrders = new HashSet<>();
        for (int s = 0; s < sections.size(); s++) {
            TemplateSection section = sections.get(s);
            String sectionPath = "sections[" + s + "]";
            if (!sectionOrders.add(section.getDisplayOrder())) {
                violations.add(new Violation(sectionPath + ".displayOrder", "Section display order is duplicated"));
            }

            List<TemplateItem> items = section.getItems().stream()
                    .sorted(Comparator.comparingInt(TemplateItem::getDisplayOrder))
                    .toList();
            if (items.isEmpty()) {
                violations.add(new Violation(
                        sectionPath + ".items",
                        "Section \"" + section.getTitle() + "\" must have at least one item"
                ));
            }

            Set<Integer> itemOrders = new HashSet<>();
            for (int i = 0; i < items.size(); i++) {
                String itemPath = sectionPath + ".items[" + i + "]";
                TemplateItem item = items.get(i);
                if (!itemOrders.add(item.getDisplayOrder())) {
                    violations.add(new Violation(itemPath + ".displayOrder", "Item display order is duplicated"));
                }
                validateItem(item, itemPath, violations);
            }
        }
        return violations;
    }

    private static void validateItem(TemplateItem item, String itemPath, List<Violation> violations) {
        ResponseType responseType = item.getResponseType();
        if (responseType != null && !MVP_RESPONSE_TYPES.contains(responseType)) {
            violations.add(new Violation(
                    itemPath + ".responseType",
                    "Response type " + responseType + " is not supported by the mobile app"
            ));
            return;
        }
        try {
            TemplateItemRules.validate(new TemplateItemCommand(
                    item.getCode(),
                    item.getTitle(),
                    item.getDescription(),
                    responseType,
                    item.isRequired(),
                    item.isObservationRequiredOnFailure(),
                    item.isEvidenceRequiredOnFailure(),
                    TemplateItemOptions.fromJson(item.getOptionsJson())
            ));
        } catch (InvalidRequestException ex) {
            violations.add(new Violation(itemPath + "." + fieldFor(ex.getCode()), ex.getMessage()));
        }
    }

    private static String fieldFor(String code) {
        return switch (code) {
            case "TEMPLATE_ITEM_RESPONSE_TYPE_REQUIRED" -> "responseType";
            case "TEMPLATE_ITEM_FAILURE_RULES_NOT_ALLOWED" -> "evidenceRequiredOnFailure";
            default -> "options";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
