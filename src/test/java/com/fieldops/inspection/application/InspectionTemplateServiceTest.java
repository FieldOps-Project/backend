package com.fieldops.inspection.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.inspection.domain.model.TemplateStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateItemRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateSectionRepository;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionTemplateServiceTest {

    @Mock
    private InspectionTemplateRepository templateRepository;

    @Mock
    private InspectionTemplateVersionRepository versionRepository;

    @Mock
    private TemplateSectionRepository sectionRepository;

    @Mock
    private TemplateItemRepository itemRepository;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    private InspectionTemplateService service;

    private UUID templateId;
    private InspectionTemplate template;
    private InspectionTemplateVersion draft;

    @BeforeEach
    void setUp() {
        service = new InspectionTemplateService(
                templateRepository, versionRepository, sectionRepository, itemRepository, authenticatedUserResolver
        );
        templateId = UUID.randomUUID();
        template = withId(InspectionTemplate.create("Extintores", null, "Incendio", UUID.randomUUID()), templateId);
        draft = withId(InspectionTemplateVersion.createDraft(template, "Extintores", null), UUID.randomUUID());

        lenient().when(templateRepository.existsById(templateId)).thenReturn(true);
        lenient().when(versionRepository.findByTemplateIdAndVersionNumber(templateId, 0)).thenReturn(Optional.of(draft));
        lenient().when(versionRepository.saveAndFlush(any(InspectionTemplateVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(sectionRepository.saveAndFlush(any(TemplateSection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(itemRepository.saveAndFlush(any(TemplateItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── Template ────────────────────────────────────────────────────────

    @Test
    void createTemplate_createsDraftTemplateWithEmptyWorkingVersion() {
        UUID authorId = UUID.randomUUID();
        when(authenticatedUserResolver.requireUserId(any())).thenReturn(authorId);
        when(templateRepository.existsByTitleIgnoreCase("Checklist eletrico")).thenReturn(false);
        when(templateRepository.saveAndFlush(any(InspectionTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InspectionTemplateVersion created = service.createTemplate(" Checklist eletrico ", " Painel ", "Eletrica");

        assertThat(created.isDraft()).isTrue();
        assertThat(created.getVersionNumber()).isZero();
        assertThat(created.getTitleSnapshot()).isEqualTo("Checklist eletrico");
        assertThat(created.getSections()).isEmpty();
        assertThat(created.getTemplate().getStatus()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(created.getTemplate().getCreatedBy()).isEqualTo(authorId);
        assertThat(created.getTemplate().getCategory()).isEqualTo("Eletrica");
    }

    @Test
    void createTemplate_rejectsDuplicatedTitle() {
        when(templateRepository.existsByTitleIgnoreCase("Extintores")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate("Extintores", null, "Incendio"))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_TITLE_ALREADY_EXISTS");
        verify(templateRepository, never()).saveAndFlush(any(InspectionTemplate.class));
    }

    @Test
    void updateTemplate_keepsDraftSnapshotInSyncWithMetadata() {
        when(templateRepository.existsByTitleIgnoreCaseAndIdNot("Extintores v2", templateId)).thenReturn(false);

        InspectionTemplateVersion updated = service.updateTemplate(templateId, "Extintores v2", "Mensal", "Incendio");

        assertThat(updated.getTemplate().getTitle()).isEqualTo("Extintores v2");
        assertThat(updated.getTitleSnapshot()).isEqualTo("Extintores v2");
        assertThat(updated.getDescriptionSnapshot()).isEqualTo("Mensal");
        verify(templateRepository).saveAndFlush(template);
    }

    @Test
    void findDraft_throwsNotFoundForUnknownTemplate() {
        UUID unknown = UUID.randomUUID();
        when(templateRepository.existsById(unknown)).thenReturn(false);

        assertThatThrownBy(() -> service.findDraft(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_TEMPLATE_NOT_FOUND");
    }

    @Test
    void findAll_rejectsUnknownSortField() {
        assertThatThrownBy(() -> service.findAll(null, null, null, 0, 20, "password,asc"))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_SORT");
    }

    // ── Sections ────────────────────────────────────────────────────────

    @Test
    void addSection_appendsToTheEndWhenNoPositionIsGiven() {
        section(draft, "Identificacao", 1);
        section(draft, "Condicoes", 2);

        TemplateSection created = service.addSection(templateId, "Evidencias", null, null);

        assertThat(created.getDisplayOrder()).isEqualTo(3);
        assertThat(draft.getSections()).hasSize(3);
    }

    @Test
    void addSection_insertsAtPositionAndShiftsFollowingSections() {
        TemplateSection first = section(draft, "Identificacao", 1);
        TemplateSection second = section(draft, "Condicoes", 2);

        TemplateSection created = service.addSection(templateId, "Acesso", null, 2);

        assertThat(created.getDisplayOrder()).isEqualTo(2);
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(second.getDisplayOrder()).isEqualTo(3);
    }

    @Test
    void addSection_clampsPositionBeyondTheEnd() {
        section(draft, "Identificacao", 1);

        TemplateSection created = service.addSection(templateId, "Evidencias", null, 99);

        assertThat(created.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void updateSection_rejectsSectionOfAnotherTemplate() {
        InspectionTemplate other = withId(InspectionTemplate.create("Outro", null, "Cat", UUID.randomUUID()), UUID.randomUUID());
        InspectionTemplateVersion otherDraft = InspectionTemplateVersion.createDraft(other, "Outro", null);
        TemplateSection foreign = section(otherDraft, "Secao", 1);
        when(sectionRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.updateSection(templateId, foreign.getId(), "Novo", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_SECTION_NOT_FOUND");
    }

    @Test
    void updateSection_rejectsSectionOfPublishedVersionWithConflict() {
        TemplateSection published = section(publishedVersion(), "Secao publicada", 1);
        when(sectionRepository.findById(published.getId())).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.updateSection(templateId, published.getId(), "Novo", null))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_VERSION_PUBLISHED");
        assertThat(published.getTitle()).isEqualTo("Secao publicada");
    }

    @Test
    void deleteSection_removesSectionAndRenumbersTheRest() {
        TemplateSection first = section(draft, "Identificacao", 1);
        TemplateSection second = section(draft, "Condicoes", 2);
        TemplateSection third = section(draft, "Evidencias", 3);
        when(sectionRepository.findById(first.getId())).thenReturn(Optional.of(first));

        service.deleteSection(templateId, first.getId());

        assertThat(draft.getSections()).containsExactly(second, third);
        assertThat(second.getDisplayOrder()).isEqualTo(1);
        assertThat(third.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void deleteSection_rejectsPublishedVersion() {
        InspectionTemplateVersion published = publishedVersion();
        TemplateSection section = section(published, "Secao publicada", 1);
        when(sectionRepository.findById(section.getId())).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.deleteSection(templateId, section.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(published.getSections()).containsExactly(section);
    }

    @Test
    void reorderSections_appliesTheFullOrderWithoutDuplicates() {
        TemplateSection a = section(draft, "A", 1);
        TemplateSection b = section(draft, "B", 2);
        TemplateSection c = section(draft, "C", 3);

        List<TemplateSection> reordered = service.reorderSections(templateId, List.of(c.getId(), a.getId(), b.getId()));

        assertThat(reordered).containsExactly(c, a, b);
        assertThat(reordered).extracting(TemplateSection::getDisplayOrder).containsExactly(1, 2, 3);
    }

    @Test
    void reorderSections_rejectsIncompleteList() {
        TemplateSection a = section(draft, "A", 1);
        section(draft, "B", 2);

        assertThatThrownBy(() -> service.reorderSections(templateId, List.of(a.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_REORDER_MISMATCH");
        assertThat(a.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void reorderSections_rejectsRepeatedOrUnknownIds() {
        TemplateSection a = section(draft, "A", 1);
        section(draft, "B", 2);

        assertThatThrownBy(() -> service.reorderSections(templateId, List.of(a.getId(), a.getId())))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.reorderSections(templateId, List.of(a.getId(), UUID.randomUUID())))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── Items ───────────────────────────────────────────────────────────

    @Test
    void addItem_storesConfiguredRulesAndOptions() {
        TemplateSection section = section(draft, "Condicoes", 1);
        when(sectionRepository.findById(section.getId())).thenReturn(Optional.of(section));

        TemplateItem created = service.addItem(templateId, section.getId(), new TemplateItemCommand(
                "EXT-01", "Estado geral", null, ResponseType.SINGLE_CHOICE, null, null, null, List.of("Bom", "Ruim")
        ), null);

        assertThat(created.getDisplayOrder()).isEqualTo(1);
        assertThat(created.isRequired()).isTrue();
        assertThat(created.isEvidenceRequiredOnFailure()).isFalse();
        assertThat(TemplateItemOptions.fromJson(created.getOptionsJson())).containsExactly("Bom", "Ruim");
        assertThat(section.getItems()).containsExactly(created);
    }

    @Test
    void addItem_insertsAtPositionAndShiftsFollowingItems() {
        TemplateSection section = section(draft, "Condicoes", 1);
        TemplateItem first = item(section, "Lacre", 1);
        TemplateItem second = item(section, "Manometro", 2);
        when(sectionRepository.findById(section.getId())).thenReturn(Optional.of(section));

        TemplateItem created = service.addItem(templateId, section.getId(), conformity("Corrosao"), 1);

        assertThat(created.getDisplayOrder()).isEqualTo(1);
        assertThat(first.getDisplayOrder()).isEqualTo(2);
        assertThat(second.getDisplayOrder()).isEqualTo(3);
    }

    @Test
    void addItem_rejectsIncoherentRulesBeforeTouchingTheSection() {
        TemplateSection section = section(draft, "Condicoes", 1);
        TemplateItem existing = item(section, "Lacre", 1);
        when(sectionRepository.findById(section.getId())).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.addItem(templateId, section.getId(), new TemplateItemCommand(
                null, "Validade", null, ResponseType.DATE, true, false, true, null
        ), 1))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ITEM_FAILURE_RULES_NOT_ALLOWED");
        assertThat(existing.getDisplayOrder()).isEqualTo(1);
        verify(itemRepository, never()).saveAndFlush(any(TemplateItem.class));
    }

    @Test
    void addItem_rejectsSectionOfPublishedVersion() {
        TemplateSection section = section(publishedVersion(), "Secao publicada", 1);
        when(sectionRepository.findById(section.getId())).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.addItem(templateId, section.getId(), conformity("Novo"), null))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_VERSION_PUBLISHED");
        assertThat(section.getItems()).isEmpty();
    }

    @Test
    void updateItem_changesTypeAndClearsOptions() {
        TemplateSection section = section(draft, "Condicoes", 1);
        TemplateItem item = item(section, "Estado", 1);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        TemplateItem updated = service.updateItem(templateId, item.getId(), new TemplateItemCommand(
                null, "Estado", null, ResponseType.TEXT_LONG, false, null, null, null
        ));

        assertThat(updated.getResponseType()).isEqualTo(ResponseType.TEXT_LONG);
        assertThat(updated.isRequired()).isFalse();
        assertThat(updated.getOptionsJson()).isNull();
    }

    @Test
    void updateItem_rejectsItemOfPublishedVersion() {
        TemplateSection section = section(publishedVersion(), "Secao publicada", 1);
        TemplateItem item = item(section, "Lacre", 1);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.updateItem(templateId, item.getId(), conformity("Alterado")))
                .isInstanceOf(ConflictException.class);
        assertThat(item.getTitle()).isEqualTo("Lacre");
    }

    @Test
    void deleteItem_removesItemAndRenumbersTheRest() {
        TemplateSection section = section(draft, "Condicoes", 1);
        TemplateItem first = item(section, "Lacre", 1);
        TemplateItem second = item(section, "Manometro", 2);
        TemplateItem third = item(section, "Corrosao", 3);
        when(itemRepository.findById(second.getId())).thenReturn(Optional.of(second));

        service.deleteItem(templateId, second.getId());

        assertThat(section.getItems()).containsExactly(first, third);
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(third.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void reorderItems_appliesTheFullOrder() {
        TemplateSection section = section(draft, "Condicoes", 1);
        TemplateItem first = item(section, "Lacre", 1);
        TemplateItem second = item(section, "Manometro", 2);
        when(sectionRepository.findById(section.getId())).thenReturn(Optional.of(section));

        service.reorderItems(templateId, section.getId(), List.of(second.getId(), first.getId()));

        assertThat(second.getDisplayOrder()).isEqualTo(1);
        assertThat(first.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void reorderItems_rejectsPublishedVersion() {
        TemplateSection section = section(publishedVersion(), "Secao publicada", 1);
        TemplateItem item = item(section, "Lacre", 1);
        when(sectionRepository.findById(section.getId())).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.reorderItems(templateId, section.getId(), List.of(item.getId())))
                .isInstanceOf(ConflictException.class);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private InspectionTemplateVersion publishedVersion() {
        return withId(
                InspectionTemplateVersion.createPublished(template, 1, "Extintores", null, UUID.randomUUID()),
                UUID.randomUUID()
        );
    }

    private static TemplateSection section(InspectionTemplateVersion version, String title, int order) {
        TemplateSection section = withId(TemplateSection.create(version, title, null, order), UUID.randomUUID());
        version.addSection(section);
        return section;
    }

    private static TemplateItem item(TemplateSection section, String title, int order) {
        TemplateItem item = withId(
                TemplateItem.create(section, null, title, null, ResponseType.CONFORMITY, true, false, false, null, order),
                UUID.randomUUID()
        );
        section.addItem(item);
        return item;
    }

    private static TemplateItemCommand conformity(String title) {
        return new TemplateItemCommand(null, title, null, ResponseType.CONFORMITY, true, true, true, null);
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
