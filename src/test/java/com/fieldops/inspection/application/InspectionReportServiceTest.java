package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionReviewRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.inspection.infrastructure.report.InspectionReportPdfGenerator;
import com.fieldops.inspection.infrastructure.storage.EvidenceStorage;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionReportServiceTest {

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final InspectionSiteRepository siteRepository = mock(InspectionSiteRepository.class);
    private final EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final InspectionResponseRepository responseRepository = mock(InspectionResponseRepository.class);
    private final InspectionEvidenceRepository evidenceRepository = mock(InspectionEvidenceRepository.class);
    private final NonConformityRepository nonConformityRepository = mock(NonConformityRepository.class);
    private final InspectionReviewRepository reviewRepository = mock(InspectionReviewRepository.class);
    private final EvidenceStorage evidenceStorage = mock(EvidenceStorage.class);
    private final AuditEventService auditEventService = mock(AuditEventService.class);
    private final AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
    private final InspectionReportPdfGenerator pdfGenerator = mock(InspectionReportPdfGenerator.class);
    private final InspectionReportService service = new InspectionReportService(
            inspectionRepository, clientRepository, siteRepository, equipmentRepository, userRepository,
            responseRepository, evidenceRepository, nonConformityRepository, reviewRepository, evidenceStorage,
            auditEventService, userResolver, pdfGenerator
    );

    @BeforeEach
    void setUp() {
        when(responseRepository.findAllByInspectionIdOrderByAnsweredAtServerAsc(any())).thenReturn(List.of());
        when(evidenceRepository.findAllByInspectionIdAndDeletedAtIsNullOrderByServerReceivedAtAsc(any())).thenReturn(List.of());
        when(nonConformityRepository.findAllByInspectionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(reviewRepository.findFirstByInspectionIdOrderByReviewCycleDesc(any())).thenReturn(Optional.empty());
        when(clientRepository.findById(any())).thenReturn(Optional.empty());
        when(siteRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findById(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supervisorCanExportOnlyAnInspectionInTheirScopeAndAuditTheExport() {
        UUID inspectionId = UUID.randomUUID();
        UUID supervisorId = UUID.randomUUID();
        User supervisor = mock(User.class);
        Inspection inspection = mock(Inspection.class);
        when(supervisor.getId()).thenReturn(supervisorId);
        when(supervisor.getRole()).thenReturn(UserRole.SUPERVISOR);
        when(userResolver.requireUser(any())).thenReturn(supervisor);
        when(inspectionRepository.findWithSnapshotsByIdAndSupervisorId(inspectionId, supervisorId))
                .thenReturn(Optional.of(inspection));
        when(inspection.getId()).thenReturn(inspectionId);
        when(inspection.getTitle()).thenReturn("Inspecao");
        when(inspection.getInstructions()).thenReturn("Executar checklist");
        when(inspection.getStatus()).thenReturn(InspectionStatus.APPROVED);
        when(inspection.getPriority()).thenReturn(InspectionPriority.MEDIUM);
        when(inspection.getScheduledFor()).thenReturn(Instant.parse("2026-11-20T09:00:00Z"));
        when(inspection.getClientId()).thenReturn(UUID.randomUUID());
        when(inspection.getSiteId()).thenReturn(UUID.randomUUID());
        when(inspection.getSupervisorId()).thenReturn(supervisorId);
        when(inspection.getItemSnapshots()).thenReturn(List.of());
        when(pdfGenerator.generate(any())).thenReturn("%PDF-1.7".getBytes());

        byte[] result = service.generate(inspectionId);

        assertThat(result).startsWith("%PDF".getBytes());
        verify(inspectionRepository).findWithSnapshotsByIdAndSupervisorId(inspectionId, supervisorId);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(auditEventService).recordInspectionEvent(
                action.capture(), any(), any(), any(), any(), any(), any()
        );
        assertThat(action.getValue()).isEqualTo("INSPECTION_REPORT_EXPORTED");
    }
}
