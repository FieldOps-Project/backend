package com.fieldops.inspection.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.NonConformity;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.domain.model.NonConformityStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionItemSnapshotRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InspectionCsvExportServiceTest {

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final InspectionItemSnapshotRepository snapshotRepository = mock(InspectionItemSnapshotRepository.class);
    private final InspectionResponseRepository responseRepository = mock(InspectionResponseRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final InspectionSiteRepository siteRepository = mock(InspectionSiteRepository.class);
    private final EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NonConformityRepository nonConformityRepository = mock(NonConformityRepository.class);
    private final AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
    private final InspectionCsvExportService service = new InspectionCsvExportService(
            inspectionRepository, snapshotRepository, responseRepository, clientRepository, siteRepository,
            equipmentRepository, userRepository, nonConformityRepository, userResolver
    );

    @Test
    void streamsInspectionRowsWithBomSeparatorAndListFields() throws Exception {
        UUID inspectionId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        User admin = mock(User.class);
        Inspection inspection = mock(Inspection.class);
        Client client = mock(Client.class);
        InspectionSite site = mock(InspectionSite.class);

        when(admin.getRole()).thenReturn(UserRole.ADMIN);
        when(userResolver.requireUser(any())).thenReturn(admin);
        when(inspectionRepository.findBy(any(Specification.class), any())).thenReturn(Stream.of(inspection));
        when(inspection.getId()).thenReturn(inspectionId);
        when(inspection.getTitle()).thenReturn("Bomba; principal");
        when(inspection.getClientId()).thenReturn(clientId);
        when(inspection.getSiteId()).thenReturn(siteId);
        when(inspection.getPriority()).thenReturn(InspectionPriority.HIGH);
        when(inspection.getStatus()).thenReturn(InspectionStatus.ASSIGNED);
        when(inspection.getScheduledFor()).thenReturn(Instant.parse("2099-11-20T09:00:00Z"));
        when(inspection.getUpdatedAt()).thenReturn(Instant.parse("2026-11-20T09:00:00Z"));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(client.getName()).thenReturn("Cliente A");
        when(site.getName()).thenReturn("Planta Sul");
        when(snapshotRepository.countApplicableItemsByInspectionId(inspectionId)).thenReturn(5L);
        when(responseRepository.countByInspectionId(inspectionId)).thenReturn(3L);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeInspections(new InspectionListQuery(
                null, null, null, null, null, null, null, null, null, null, false, 0, 1, "scheduledFor,asc"
        ), output);

        String csv = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFid;title;");
        assertThat(csv).contains("\"Bomba; principal\"", "Cliente A", ";3;5;60;");
    }

    @Test
    void streamsNonConformitiesUsingSupervisorScopeAndEscapesText() throws Exception {
        UUID inspectionId = UUID.randomUUID();
        UUID nonConformityId = UUID.randomUUID();
        User supervisor = mock(User.class);
        NonConformity nonConformity = mock(NonConformity.class);

        when(supervisor.getRole()).thenReturn(UserRole.SUPERVISOR);
        when(supervisor.getId()).thenReturn(UUID.randomUUID());
        when(userResolver.requireUser(any())).thenReturn(supervisor);
        when(nonConformityRepository.streamForExport(
                inspectionId, NonConformitySeverity.HIGH, NonConformityStatus.OPEN, null,
                null, supervisor.getId()
        )).thenReturn(Stream.of(nonConformity));
        when(nonConformity.getId()).thenReturn(nonConformityId);
        when(nonConformity.getInspectionId()).thenReturn(inspectionId);
        when(nonConformity.getTitle()).thenReturn("Pressao");
        when(nonConformity.getDescription()).thenReturn("Verificar; novamente");
        when(nonConformity.getSeverity()).thenReturn(NonConformitySeverity.HIGH);
        when(nonConformity.getStatus()).thenReturn(NonConformityStatus.OPEN);
        when(nonConformity.getCreatedAtDevice()).thenReturn(Instant.parse("2026-11-20T09:00:00Z"));
        when(nonConformity.getServerReceivedAt()).thenReturn(Instant.parse("2026-11-20T09:01:00Z"));
        when(nonConformity.getVersion()).thenReturn(0);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeNonConformities(inspectionId, NonConformitySeverity.HIGH, NonConformityStatus.OPEN, null, output);

        String csv = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFid;inspection_id;");
        assertThat(csv).contains("Pressao;\"Verificar; novamente\";HIGH;OPEN");
    }
}
