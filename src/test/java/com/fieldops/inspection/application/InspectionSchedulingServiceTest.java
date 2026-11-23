package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.domain.model.EquipmentStatus;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.inspection.presentation.dto.CreateInspectionRequest;
import com.fieldops.inspection.presentation.dto.UpdateInspectionRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionSchedulingServiceTest {

    @Mock
    private InspectionRepository inspectionRepository;
    @Mock
    private ObjectProvider<InspectionRepository> inspectionRepositoryProvider;
    @Mock
    private InspectionTemplateVersionRepository versionRepository;
    @Mock
    private TemplateVersionService templateVersionService;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private InspectionSiteRepository siteRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditEventService auditEventService;
    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    private InspectionSchedulingService service;
    private UUID actorId;
    private UUID clientId;
    private UUID siteId;
    private UUID equipmentId;
    private UUID technicianId;
    private UUID supervisorId;
    private InspectionTemplateVersion version;
    private Client client;
    private InspectionSite site;
    private Equipment equipment;
    private User technician;
    private User supervisor;

    @BeforeEach
    void setUp() {
        when(inspectionRepositoryProvider.getIfAvailable()).thenReturn(inspectionRepository);
        service = new InspectionSchedulingService(
                inspectionRepositoryProvider,
                versionRepository,
                templateVersionService,
                clientRepository,
                siteRepository,
                equipmentRepository,
                userRepository,
                auditEventService,
                authenticatedUserResolver
        );

        actorId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        equipmentId = UUID.randomUUID();
        technicianId = UUID.randomUUID();
        supervisorId = UUID.randomUUID();

        client = withId(Client.create("Cliente", null, null, null, null, null), clientId);
        site = withId(InspectionSite.create(
                client, "Planta Sul", null, null, null, null, null,
                null, null, null, null, InspectionSiteStatus.ACTIVE
        ), siteId);
        equipment = withId(Equipment.create(
                siteId, "Extintor", null, null, null, null, null,
                "QR-1", EquipmentStatus.ACTIVE, null
        ), equipmentId);
        technician = withId(User.create(
                "Tecnico", "tecnico@example.com", "hash", UserRole.TECHNICIAN, UserStatus.ACTIVE, null
        ), technicianId);
        supervisor = withId(User.create(
                "Supervisor", "supervisor@example.com", "hash", UserRole.SUPERVISOR, UserStatus.ACTIVE, null
        ), supervisorId);

        InspectionTemplate template = InspectionTemplate.create(
                "Checklist de extintores", null, "Incendio", supervisorId
        );
        template.updateStatus(com.fieldops.inspection.domain.model.TemplateStatus.ACTIVE);
        version = withId(InspectionTemplateVersion.createPublished(
                template, 1, "Checklist de extintores", null, supervisorId
        ), UUID.randomUUID());
        TemplateSection section = TemplateSection.create(version, "Condicoes", null, 1);
        section.addItem(TemplateItem.create(
                section, "EXT-01", "Lacre intacto?", "Verifique o lacre.",
                ResponseType.CONFORMITY, true, true, false, null, 1
        ));
        section.addItem(TemplateItem.create(
                section, "EXT-02", "Pintura", null,
                ResponseType.SINGLE_CHOICE, false, false, false, "[\"Bom\",\"Ruim\"]", 2
        ));
        version.addSection(section);

        when(authenticatedUserResolver.requireUserId(any())).thenReturn(actorId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(equipmentRepository.findById(equipmentId)).thenReturn(Optional.of(equipment));
        when(userRepository.findById(technicianId)).thenReturn(Optional.of(technician));
        when(userRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));
        when(templateVersionService.findVersionForScheduling(version.getId())).thenReturn(version);
        when(inspectionRepository.saveAndFlush(any(Inspection.class))).thenAnswer(invocation -> {
            Inspection inspection = invocation.getArgument(0);
            ReflectionTestUtils.setField(inspection, "id", UUID.randomUUID());
            return inspection;
        });
    }

    @Test
    void scheduleCreatesAssignedInspectionWithOrderedFrozenSnapshotsAndAudit() {
        Inspection saved = service.schedule(new CreateInspectionRequest(
                version.getId(), clientId, siteId, equipmentId, technicianId, supervisorId,
                "Inspecao de extintor", "Levar lanterna", null,
                Instant.parse("2026-11-10T13:00:00Z")
        ));

        assertThat(saved.getStatus()).isEqualTo(InspectionStatus.ASSIGNED);
        assertThat(saved.getCreatedBy()).isEqualTo(actorId);
        assertThat(saved.getItemSnapshots()).extracting("itemTitle")
                .containsExactly("Lacre intacto?", "Pintura");
        assertThat(saved.getItemSnapshots().get(0).getItemDescription())
                .isEqualTo("Verifique o lacre.");
        verify(auditEventService).recordEvent(
                eq("INSPECTION_CREATED"), eq("INSPECTION"), eq(saved.getId()), eq(null), any(String.class)
        );
        verify(auditEventService).recordEvent(
                eq("INSPECTION_ASSIGNED"), eq("INSPECTION"), eq(saved.getId()), eq(null),
                eq("technicianId=" + technicianId)
        );
    }

    @Test
    void scheduleRejectsSiteFromAnotherClientBeforePersisting() {
        UUID otherClientId = UUID.randomUUID();
        Client otherClient = withId(Client.create("Outro", null, null, null, null, null), otherClientId);
        InspectionSite otherSite = withId(InspectionSite.create(
                otherClient, "Outro local", null, null, null, null, null,
                null, null, null, null, InspectionSiteStatus.ACTIVE
        ), UUID.randomUUID());
        when(siteRepository.findById(otherSite.getId())).thenReturn(Optional.of(otherSite));

        assertThatThrownBy(() -> service.schedule(new CreateInspectionRequest(
                version.getId(), clientId, otherSite.getId(), equipmentId, technicianId, supervisorId,
                "Inspecao", null, null, Instant.now()
        ))).isInstanceOfSatisfying(BusinessRuleException.class, ex ->
                assertThat(ex.getCode()).isEqualTo("SITE_CLIENT_MISMATCH"));

        verify(inspectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void scheduleRejectsInactiveOrWrongRoleTechnician() {
        User supervisorAsTechnician = withId(User.create(
                "Supervisor", "other@example.com", "hash", UserRole.SUPERVISOR, UserStatus.ACTIVE, null
        ), technicianId);
        when(userRepository.findById(technicianId)).thenReturn(Optional.of(supervisorAsTechnician));

        assertThatThrownBy(() -> service.schedule(new CreateInspectionRequest(
                version.getId(), clientId, siteId, equipmentId, technicianId, supervisorId,
                "Inspecao", null, null, Instant.now()
        ))).isInstanceOfSatisfying(BusinessRuleException.class, ex ->
                assertThat(ex.getCode()).isEqualTo("INVALID_TECHNICIAN"));

        verify(inspectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void scheduleWithoutTechnicianCreatesDraftForSiteInspection() {
        InspectionTemplate siteTemplate = InspectionTemplate.create(
                "Checklist de local", null, "Ambiente", supervisorId
        );
        siteTemplate.updateStatus(com.fieldops.inspection.domain.model.TemplateStatus.ACTIVE);
        InspectionTemplateVersion siteVersion = withId(
                InspectionTemplateVersion.createPublished(siteTemplate, 1, "Checklist de local", null, supervisorId),
                UUID.randomUUID()
        );
        when(templateVersionService.findVersionForScheduling(siteVersion.getId())).thenReturn(siteVersion);

        Inspection saved = service.schedule(new CreateInspectionRequest(
                siteVersion.getId(), clientId, siteId, null, null, supervisorId,
                "Inspecao de local", null, null, Instant.now()
        ));

        assertThat(saved.getStatus()).isEqualTo(InspectionStatus.DRAFT);
        assertThat(saved.getTechnicianId()).isNull();
        verify(auditEventService).recordEvent(
                eq("INSPECTION_CREATED"), eq("INSPECTION"), eq(saved.getId()), eq(null), any(String.class)
        );
    }

    @Test
    void updateChangesPlanningDataButKeepsTheExistingSnapshots() {
        Inspection initial = service.schedule(new CreateInspectionRequest(
                version.getId(), clientId, siteId, equipmentId, technicianId, supervisorId,
                "Inspecao", null, null, Instant.now()
        ));
        when(inspectionRepository.findWithSnapshotsById(initial.getId())).thenReturn(Optional.of(initial));
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        String originalItemTitle = initial.getItemSnapshots().get(0).getItemTitle();

        Inspection updated = service.update(initial.getId(), new UpdateInspectionRequest(
                clientId, siteId, equipmentId, technicianId, supervisorId,
                "Inspecao remarcada", "Acesso pela portaria 2", null,
                Instant.parse("2026-11-11T13:00:00Z")
        ));

        assertThat(updated.getTitle()).isEqualTo("Inspecao remarcada");
        assertThat(updated.getItemSnapshots().get(0).getItemTitle()).isEqualTo(originalItemTitle);
        verify(auditEventService).recordEvent(
                eq("INSPECTION_UPDATED"), eq("INSPECTION"), eq(initial.getId()), eq(null), any(String.class)
        );
    }

    @Test
    void scheduleRejectsMissingDateWithBusinessErrorWhenCalledOutsideWebValidation() {
        assertThatThrownBy(() -> service.schedule(new CreateInspectionRequest(
                version.getId(), clientId, siteId, equipmentId, technicianId, supervisorId,
                "Inspecao", null, null, null
        ))).isInstanceOfSatisfying(BusinessRuleException.class, ex ->
                assertThat(ex.getCode()).isEqualTo("SCHEDULED_FOR_REQUIRED"));
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
