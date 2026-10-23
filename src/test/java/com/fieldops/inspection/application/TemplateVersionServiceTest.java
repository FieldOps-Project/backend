package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.inspection.domain.model.TemplateStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateVersionServiceTest {

    @Mock
    private InspectionTemplateRepository templateRepository;

    @Mock
    private InspectionTemplateVersionRepository versionRepository;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    private TemplateVersionService service;

    private UUID templateId;
    private InspectionTemplate template;
    private InspectionTemplateVersion draft;

    @BeforeEach
    void setUp() {
        service = new TemplateVersionService(
                templateRepository, versionRepository, auditEventService, authenticatedUserResolver
        );
        templateId = UUID.randomUUID();
        template = withId(InspectionTemplate.create("Extintores", "Mensal", "Incendio", UUID.randomUUID()), templateId);
        draft = withId(InspectionTemplateVersion.createDraft(template, "Extintores", "Mensal"), UUID.randomUUID());

        lenient().when(templateRepository.findByIdForUpdate(templateId)).thenReturn(Optional.of(template));
        lenient().when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        lenient().when(versionRepository.findByTemplateIdAndVersionNumber(templateId, 0)).thenReturn(Optional.of(draft));
        lenient().when(versionRepository.saveAndFlush(any(InspectionTemplateVersion.class)))
                .thenAnswer(invocation -> {
                    InspectionTemplateVersion version = invocation.getArgument(0);
                    return version.getId() == null ? withId(version, UUID.randomUUID()) : version;
                });
        lenient().when(templateRepository.saveAndFlush(any(InspectionTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── Publish ─────────────────────────────────────────────────────────

    @Test
    void publish_freezesCopyAsNextVersionAndKeepsDraftEditable() {
        addValidStructure();
        UUID publisher = UUID.randomUUID();
        when(authenticatedUserResolver.requireUserId(any())).thenReturn(publisher);
        when(versionRepository.findLatestVersionNumber(templateId)).thenReturn(2);

        InspectionTemplateVersion published = service.publish(templateId);

        assertThat(published.getVersionNumber()).isEqualTo(3);
        assertThat(published.isDraft()).isFalse();
        assertThat(published.getPublishedBy()).isEqualTo(publisher);
        assertThat(published.getSections()).hasSize(1);
        assertThat(published.getSections().get(0)).isNotSameAs(draft.getSections().get(0));
        assertThat(draft.isDraft()).isTrue();
        assertThat(draft.getSections()).hasSize(1);
        assertThat(template.getCurrentVersion()).isEqualTo(3);
        assertThat(template.getStatus()).isEqualTo(TemplateStatus.ACTIVE);
        verify(auditEventService).recordEvent(
                eq("TEMPLATE_VERSION_PUBLISHED"),
                eq("INSPECTION_TEMPLATE_VERSION"),
                eq(published.getId()),
                isNull(),
                eq("{\"templateId\":\"" + templateId + "\",\"versionNumber\":3}")
        );
    }

    @Test
    void publish_firstVersionIsNumberOne() {
        addValidStructure();
        when(authenticatedUserResolver.requireUserId(any())).thenReturn(UUID.randomUUID());
        when(versionRepository.findLatestVersionNumber(templateId)).thenReturn(0);

        assertThat(service.publish(templateId).getVersionNumber()).isEqualTo(1);
    }

    @Test
    void publish_keepsRetiredTemplateInactive() {
        addValidStructure();
        template.advanceCurrentVersion(1);
        template.updateStatus(TemplateStatus.INACTIVE);
        when(authenticatedUserResolver.requireUserId(any())).thenReturn(UUID.randomUUID());
        when(versionRepository.findLatestVersionNumber(templateId)).thenReturn(1);

        service.publish(templateId);

        assertThat(template.getCurrentVersion()).isEqualTo(2);
        assertThat(template.getStatus()).isEqualTo(TemplateStatus.INACTIVE);
    }

    @Test
    void publish_rejectsInvalidDraftListingEveryViolation() {
        TemplateSection empty = TemplateSection.create(draft, "Vazia", null, 1);
        draft.addSection(empty);
        TemplateSection second = TemplateSection.create(draft, "Escolha", null, 2);
        second.addItem(TemplateItem.create(
                second, null, "Lacre", null, ResponseType.SINGLE_CHOICE, true, false, false, null, 1
        ));
        draft.addSection(second);

        assertThatThrownBy(() -> service.publish(templateId))
                .isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("TEMPLATE_PUBLISH_INVALID");
                    assertThat(ex.getViolations())
                            .extracting("field")
                            .containsExactly("sections[0].items", "sections[1].items[0].options");
                });
        verify(versionRepository, never()).saveAndFlush(any(InspectionTemplateVersion.class));
        verifyNoInteractions(auditEventService);
        assertThat(template.getStatus()).isEqualTo(TemplateStatus.DRAFT);
    }

    @Test
    void publish_unknownTemplateIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(templateRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_TEMPLATE_NOT_FOUND");
    }

    // ── Versions ────────────────────────────────────────────────────────

    @Test
    void findVersion_hidesDraftBehindNotFound() {
        when(versionRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.findVersion(draft.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_VERSION_NOT_FOUND");
    }

    @Test
    void findVersions_requiresExistingTemplate() {
        UUID unknown = UUID.randomUUID();
        when(templateRepository.existsById(unknown)).thenReturn(false);

        assertThatThrownBy(() -> service.findVersions(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAvailability_retiresVersionAndAudits() {
        InspectionTemplateVersion published = published(true);

        InspectionTemplateVersion updated = service.updateAvailability(published.getId(), false);

        assertThat(updated.isActiveForNewInspections()).isFalse();
        verify(auditEventService).recordEvent(
                "TEMPLATE_VERSION_AVAILABILITY_CHANGED",
                "INSPECTION_TEMPLATE_VERSION",
                published.getId(),
                "{\"activeForNewInspections\":true}",
                "{\"activeForNewInspections\":false}"
        );
    }

    @Test
    void updateAvailability_withoutChangeSkipsAudit() {
        InspectionTemplateVersion published = published(true);

        service.updateAvailability(published.getId(), true);

        verifyNoInteractions(auditEventService);
    }

    // ── Template status ─────────────────────────────────────────────────

    @Test
    void updateTemplateStatus_retiresPublishedTemplate() {
        template.advanceCurrentVersion(1);
        template.updateStatus(TemplateStatus.ACTIVE);

        InspectionTemplate updated = service.updateTemplateStatus(templateId, TemplateStatus.INACTIVE);

        assertThat(updated.getStatus()).isEqualTo(TemplateStatus.INACTIVE);
        verify(auditEventService).recordEvent(
                eq("TEMPLATE_STATUS_CHANGED"), eq("INSPECTION_TEMPLATE"), eq(templateId), anyString(), anyString()
        );
    }

    @Test
    void updateTemplateStatus_rejectsDraftStatus() {
        assertThatThrownBy(() -> service.updateTemplateStatus(templateId, TemplateStatus.DRAFT))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_TEMPLATE_STATUS");
    }

    @Test
    void updateTemplateStatus_requiresPublication() {
        assertThatThrownBy(() -> service.updateTemplateStatus(templateId, TemplateStatus.ACTIVE))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_NOT_PUBLISHED");
    }

    // ── Scheduling guard ────────────────────────────────────────────────

    @Test
    void findVersionForScheduling_acceptsAvailableVersionOfActiveTemplate() {
        template.updateStatus(TemplateStatus.ACTIVE);
        InspectionTemplateVersion published = published(true);

        assertThat(service.findVersionForScheduling(published.getId())).isSameAs(published);
    }

    @Test
    void findVersionForScheduling_rejectsRetiredVersion() {
        template.updateStatus(TemplateStatus.ACTIVE);
        InspectionTemplateVersion published = published(false);

        assertThatThrownBy(() -> service.findVersionForScheduling(published.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_VERSION_UNAVAILABLE");
    }

    @Test
    void findVersionForScheduling_rejectsInactiveTemplate() {
        template.updateStatus(TemplateStatus.INACTIVE);
        InspectionTemplateVersion published = published(true);

        assertThatThrownBy(() -> service.findVersionForScheduling(published.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_VERSION_UNAVAILABLE");
    }

    private void addValidStructure() {
        TemplateSection section = TemplateSection.create(draft, "Condicoes", null, 1);
        section.addItem(TemplateItem.create(
                section, "EXT-01", "Lacre integro?", null, ResponseType.CONFORMITY, true, true, true, null, 1
        ));
        draft.addSection(section);
    }

    private InspectionTemplateVersion published(boolean active) {
        InspectionTemplateVersion version = withId(
                InspectionTemplateVersion.createPublished(template, 1, "Extintores", null, UUID.randomUUID()),
                UUID.randomUUID()
        );
        if (!active) {
            version.updateAvailability(false);
        }
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        return version;
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
