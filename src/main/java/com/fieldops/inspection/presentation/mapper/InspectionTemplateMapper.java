package com.fieldops.inspection.presentation.mapper;

import com.fieldops.inspection.application.TemplateItemOptions;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.inspection.presentation.dto.InspectionTemplateDetailResponse;
import com.fieldops.inspection.presentation.dto.InspectionTemplateResponse;
import com.fieldops.inspection.presentation.dto.TemplateItemResponse;
import com.fieldops.inspection.presentation.dto.TemplateSectionResponse;
import com.fieldops.inspection.presentation.dto.TemplateVersionDetailResponse;
import com.fieldops.inspection.presentation.dto.TemplateVersionSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class InspectionTemplateMapper {

    public InspectionTemplateResponse toResponse(InspectionTemplate template) {
        return new InspectionTemplateResponse(
                template.getId(),
                template.getTitle(),
                template.getDescription(),
                template.getCategory(),
                template.getStatus(),
                template.getCurrentVersion(),
                template.getCreatedBy(),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                template.getVersion()
        );
    }

    public InspectionTemplateDetailResponse toDetail(InspectionTemplateVersion draft) {
        InspectionTemplate template = draft.getTemplate();
        return new InspectionTemplateDetailResponse(
                template.getId(),
                template.getTitle(),
                template.getDescription(),
                template.getCategory(),
                template.getStatus(),
                template.getCurrentVersion(),
                draft.getId(),
                toSections(draft.getSections()),
                template.getCreatedBy(),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                template.getVersion()
        );
    }

    public TemplateVersionSummaryResponse toVersionSummary(InspectionTemplateVersion version) {
        return new TemplateVersionSummaryResponse(
                version.getId(),
                version.getTemplate().getId(),
                version.getVersionNumber(),
                version.getTitleSnapshot(),
                version.getPublishedBy(),
                version.getPublishedAt(),
                version.isActiveForNewInspections()
        );
    }

    public TemplateVersionDetailResponse toVersionDetail(InspectionTemplateVersion version) {
        return new TemplateVersionDetailResponse(
                version.getId(),
                version.getTemplate().getId(),
                version.getVersionNumber(),
                version.getTitleSnapshot(),
                version.getDescriptionSnapshot(),
                version.getPublishedBy(),
                version.getPublishedAt(),
                version.isActiveForNewInspections(),
                toSections(version.getSections())
        );
    }

    public List<TemplateSectionResponse> toSections(List<TemplateSection> sections) {
        return sections.stream()
                .sorted(Comparator.comparingInt(TemplateSection::getDisplayOrder))
                .map(this::toSection)
                .toList();
    }

    public TemplateSectionResponse toSection(TemplateSection section) {
        return new TemplateSectionResponse(
                section.getId(),
                section.getTitle(),
                section.getDescription(),
                section.getDisplayOrder(),
                section.getItems().stream()
                        .sorted(Comparator.comparingInt(TemplateItem::getDisplayOrder))
                        .map(this::toItem)
                        .toList()
        );
    }

    public TemplateItemResponse toItem(TemplateItem item) {
        return new TemplateItemResponse(
                item.getId(),
                item.getSection().getId(),
                item.getCode(),
                item.getTitle(),
                item.getDescription(),
                item.getResponseType(),
                item.isRequired(),
                item.isObservationRequiredOnFailure(),
                item.isEvidenceRequiredOnFailure(),
                TemplateItemOptions.fromJson(item.getOptionsJson()),
                item.getDisplayOrder()
        );
    }
}
