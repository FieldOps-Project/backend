package com.fieldops.inspection.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.inspection.domain.model.TemplateStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateSpecifications;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateItemRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateSectionRepository;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.presentation.dto.PaginationDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Draft editing use cases of the template builder (UC-04).
 *
 * <p>Every structural change targets the working draft ({@code version_number = 0},
 * see ADR-001). Sections and items of published versions are rejected with 409.</p>
 */
@Service
public class InspectionTemplateService {

    static final int DRAFT_VERSION_NUMBER = 0;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "title", "category", "status", "createdAt", "updatedAt"
    );

    private final InspectionTemplateRepository templateRepository;
    private final InspectionTemplateVersionRepository versionRepository;
    private final TemplateSectionRepository sectionRepository;
    private final TemplateItemRepository itemRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public InspectionTemplateService(
            InspectionTemplateRepository templateRepository,
            InspectionTemplateVersionRepository versionRepository,
            TemplateSectionRepository sectionRepository,
            TemplateItemRepository itemRepository,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.sectionRepository = sectionRepository;
        this.itemRepository = itemRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    // ── Template ────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public InspectionTemplateVersion createTemplate(String title, String description, String category) {
        ensureUniqueTitle(title, null);
        UUID createdBy = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );

        InspectionTemplate template = templateRepository.saveAndFlush(
                InspectionTemplate.create(title, description, category, createdBy)
        );
        return versionRepository.saveAndFlush(InspectionTemplateVersion.createDraft(
                template, template.getTitle(), template.getDescription()
        ));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public Page<InspectionTemplate> findAll(
            String search,
            TemplateStatus status,
            String category,
            int page,
            int size,
            String sort
    ) {
        return templateRepository.findAll(
                InspectionTemplateSpecifications.withFilters(search, status, category),
                PaginationDefaults.of(page, size, parseSort(sort))
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public InspectionTemplateVersion findDraft(UUID templateId) {
        return requireDraft(templateId);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public InspectionTemplateVersion updateTemplate(UUID templateId, String title, String description, String category) {
        InspectionTemplateVersion draft = requireDraft(templateId);
        ensureUniqueTitle(title, templateId);

        InspectionTemplate template = draft.getTemplate();
        template.updateMetadata(title, description, category);
        draft.updateSnapshot(template.getTitle(), template.getDescription());
        templateRepository.saveAndFlush(template);
        return draft;
    }

    // ── Sections ────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public TemplateSection addSection(UUID templateId, String title, String description, Integer position) {
        InspectionTemplateVersion draft = requireDraft(templateId);
        List<TemplateSection> sections = sorted(draft.getSections(), TemplateSection::getDisplayOrder);
        int displayOrder = openSlot(sections, position, TemplateSection::getDisplayOrder, TemplateSection::updateDisplayOrder);

        TemplateSection section = TemplateSection.create(draft, title, description, displayOrder);
        draft.addSection(section);
        return sectionRepository.saveAndFlush(section);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public TemplateSection updateSection(UUID templateId, UUID sectionId, String title, String description) {
        TemplateSection section = requireSection(templateId, sectionId);
        section.getTemplateVersion().ensureEditable();
        section.updateMetadata(title, description);
        return sectionRepository.saveAndFlush(section);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public void deleteSection(UUID templateId, UUID sectionId) {
        TemplateSection section = requireSection(templateId, sectionId);
        InspectionTemplateVersion version = section.getTemplateVersion();
        version.ensureEditable();

        version.removeSection(section);
        renumber(sorted(version.getSections(), TemplateSection::getDisplayOrder), TemplateSection::updateDisplayOrder);
        versionRepository.saveAndFlush(version);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public List<TemplateSection> reorderSections(UUID templateId, List<UUID> sectionIds) {
        InspectionTemplateVersion draft = requireDraft(templateId);
        applyOrder(draft.getSections(), sectionIds, TemplateSection::getId, TemplateSection::updateDisplayOrder);
        versionRepository.saveAndFlush(draft);
        return sorted(draft.getSections(), TemplateSection::getDisplayOrder);
    }

    // ── Items ───────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public TemplateItem addItem(UUID templateId, UUID sectionId, TemplateItemCommand command, Integer position) {
        TemplateSection section = requireSection(templateId, sectionId);
        section.getTemplateVersion().ensureEditable();
        List<String> options = TemplateItemRules.validate(command);

        List<TemplateItem> items = sorted(section.getItems(), TemplateItem::getDisplayOrder);
        int displayOrder = openSlot(items, position, TemplateItem::getDisplayOrder, TemplateItem::updateDisplayOrder);

        TemplateItem item = TemplateItem.create(
                section,
                command.code(),
                command.title(),
                command.description(),
                command.responseType(),
                command.isRequired(),
                command.isObservationRequiredOnFailure(),
                command.isEvidenceRequiredOnFailure(),
                TemplateItemOptions.toJson(options),
                displayOrder
        );
        section.addItem(item);
        return itemRepository.saveAndFlush(item);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public TemplateItem updateItem(UUID templateId, UUID itemId, TemplateItemCommand command) {
        TemplateItem item = requireItem(templateId, itemId);
        item.getSection().getTemplateVersion().ensureEditable();
        List<String> options = TemplateItemRules.validate(command);

        item.updateMetadata(
                command.code(),
                command.title(),
                command.description(),
                command.responseType(),
                command.isRequired(),
                command.isObservationRequiredOnFailure(),
                command.isEvidenceRequiredOnFailure(),
                TemplateItemOptions.toJson(options)
        );
        return itemRepository.saveAndFlush(item);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public void deleteItem(UUID templateId, UUID itemId) {
        TemplateItem item = requireItem(templateId, itemId);
        TemplateSection section = item.getSection();
        section.getTemplateVersion().ensureEditable();

        section.removeItem(item);
        renumber(sorted(section.getItems(), TemplateItem::getDisplayOrder), TemplateItem::updateDisplayOrder);
        sectionRepository.saveAndFlush(section);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public TemplateSection reorderItems(UUID templateId, UUID sectionId, List<UUID> itemIds) {
        TemplateSection section = requireSection(templateId, sectionId);
        section.getTemplateVersion().ensureEditable();
        applyOrder(section.getItems(), itemIds, TemplateItem::getId, TemplateItem::updateDisplayOrder);
        return sectionRepository.saveAndFlush(section);
    }

    // ── Lookups ─────────────────────────────────────────────────────────

    private InspectionTemplateVersion requireDraft(UUID templateId) {
        if (!templateRepository.existsById(templateId)) {
            throw new ResourceNotFoundException(
                    "INSPECTION_TEMPLATE_NOT_FOUND",
                    "Inspection template with id " + templateId + " not found"
            );
        }
        return versionRepository.findByTemplateIdAndVersionNumber(templateId, DRAFT_VERSION_NUMBER)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEMPLATE_DRAFT_NOT_FOUND",
                        "Inspection template " + templateId + " has no draft version"
                ));
    }

    private TemplateSection requireSection(UUID templateId, UUID sectionId) {
        return sectionRepository.findById(sectionId)
                .filter(section -> templateId.equals(section.getTemplateVersion().getTemplate().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEMPLATE_SECTION_NOT_FOUND",
                        "Template section with id " + sectionId + " not found"
                ));
    }

    private TemplateItem requireItem(UUID templateId, UUID itemId) {
        return itemRepository.findById(itemId)
                .filter(item -> templateId.equals(item.getSection().getTemplateVersion().getTemplate().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEMPLATE_ITEM_NOT_FOUND",
                        "Template item with id " + itemId + " not found"
                ));
    }

    private void ensureUniqueTitle(String title, UUID templateId) {
        if (title == null || title.isBlank()) {
            return;
        }
        String normalized = title.trim();
        boolean taken = templateId == null
                ? templateRepository.existsByTitleIgnoreCase(normalized)
                : templateRepository.existsByTitleIgnoreCaseAndIdNot(normalized, templateId);
        if (taken) {
            throw new ConflictException(
                    "TEMPLATE_TITLE_ALREADY_EXISTS",
                    "An inspection template with this title already exists"
            );
        }
    }

    // ── Ordering (RN-017) ───────────────────────────────────────────────

    private static <T> List<T> sorted(List<T> elements, ToIntFunction<T> orderOf) {
        return elements.stream().sorted(Comparator.comparingInt(orderOf)).toList();
    }

    /**
     * Returns the 1-based position for a new element, shifting the elements at
     * and after it. A missing or out-of-range position appends to the end.
     */
    private static <T> int openSlot(
            List<T> ordered,
            Integer requested,
            ToIntFunction<T> orderOf,
            ObjIntConsumer<T> setOrder
    ) {
        int position = requested == null ? ordered.size() + 1 : Math.min(Math.max(requested, 1), ordered.size() + 1);
        for (T element : ordered) {
            int current = orderOf.applyAsInt(element);
            if (current >= position) {
                setOrder.accept(element, current + 1);
            }
        }
        return position;
    }

    private static <T> void renumber(List<T> ordered, ObjIntConsumer<T> setOrder) {
        for (int i = 0; i < ordered.size(); i++) {
            setOrder.accept(ordered.get(i), i + 1);
        }
    }

    private static <T> void applyOrder(
            List<T> elements,
            List<UUID> requestedIds,
            Function<T, UUID> idOf,
            ObjIntConsumer<T> setOrder
    ) {
        Map<UUID, T> byId = elements.stream().collect(Collectors.toMap(idOf, Function.identity()));
        boolean sameElements = requestedIds.size() == byId.size()
                && new HashSet<>(requestedIds).size() == requestedIds.size()
                && byId.keySet().containsAll(requestedIds);
        if (!sameElements) {
            throw new BusinessRuleException(
                    "TEMPLATE_REORDER_MISMATCH",
                    "The new order must list every current element exactly once"
            );
        }
        for (int i = 0; i < requestedIds.size(); i++) {
            setOrder.accept(byId.get(requestedIds.get(i)), i + 1);
        }
    }

    private Sort parseSort(String sort) {
        String requestedSort = sort == null || sort.isBlank() ? "createdAt,desc" : sort.trim();
        String[] parts = requestedSort.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank() || !ALLOWED_SORT_FIELDS.contains(parts[0].trim())) {
            throw invalidSort();
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException ex) {
                throw invalidSort();
            }
        }
        return Sort.by(direction, parts[0].trim());
    }

    private InvalidRequestException invalidSort() {
        return new InvalidRequestException(
                "INVALID_SORT",
                "Sort must use an allowed template field and direction asc or desc"
        );
    }
}
