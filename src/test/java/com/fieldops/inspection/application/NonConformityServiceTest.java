package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.NonConformity;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.inspection.presentation.dto.CreateNonConformityRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NonConformityServiceTest {

    @Mock
    private NonConformityRepository repository;
    @Mock
    private InspectionRepository inspectionRepository;
    @Mock
    private InspectionResponseRepository responseRepository;
    @Mock
    private InspectionEvidenceRepository evidenceRepository;
    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;
    @Mock
    private AuditEventService auditEventService;

    private NonConformityService service;
    private UUID inspectionId;
    private UUID technicianId;
    private Authentication authentication;
    private Inspection inspection;

    @BeforeEach
    void setUp() {
        service = new NonConformityService(
                repository, inspectionRepository, responseRepository, evidenceRepository,
                authenticatedUserResolver, auditEventService
        );
        inspectionId = UUID.randomUUID();
        technicianId = UUID.randomUUID();
        inspection = org.mockito.Mockito.mock(Inspection.class);
        lenient().when(inspection.getId()).thenReturn(inspectionId);
        lenient().when(inspection.getItemSnapshots()).thenReturn(List.of());
        authentication = new TestingAuthenticationToken("technician", "password");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        lenient().when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(technicianId);
        lenient().when(repository.saveAndFlush(any(NonConformity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsCriticalNonConformityWithoutEvidenceDuringOfflineSync() {
        UUID id = UUID.randomUUID();
        CreateNonConformityRequest request = request(id, NonConformitySeverity.CRITICAL, null);
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        when(repository.findById(id)).thenReturn(Optional.empty());

        NonConformity result = service.create(inspectionId, request);

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getSeverity()).isEqualTo(NonConformitySeverity.CRITICAL);
        verify(auditEventService).recordInspectionEvent(
                org.mockito.ArgumentMatchers.eq("NON_CONFORMITY_CREATED"),
                org.mockito.ArgumentMatchers.eq("NON_CONFORMITY"),
                org.mockito.ArgumentMatchers.eq(id),
                any(),
                any(),
                org.mockito.ArgumentMatchers.isNull(),
                any(String.class)
        );
    }

    @Test
    void retriesWithTheSameDeviceIdWithoutCreatingADuplicate() {
        UUID id = UUID.randomUUID();
        NonConformity existing = NonConformity.create(
                id, inspectionId, null, null, "Existing", "Already synced", NonConformitySeverity.HIGH,
                technicianId, Instant.parse("2026-11-23T10:00:00Z"), Instant.parse("2026-11-23T11:00:00Z")
        );
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThat(service.create(inspectionId, request(id, NonConformitySeverity.CRITICAL, null)))
                .isSameAs(existing);
        verify(repository, never()).saveAndFlush(any(NonConformity.class));
        verify(auditEventService, never()).recordInspectionEvent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAResponseFromAnotherInspection() {
        UUID responseId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(responseRepository.findByIdAndInspectionId(responseId, inspectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(inspectionId, request(id, NonConformitySeverity.HIGH, responseId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "NON_CONFORMITY_RESPONSE_NOT_IN_INSPECTION");
        verify(repository, never()).saveAndFlush(any(NonConformity.class));
    }

    @Test
    void detectsCriticalRecordsThatStillNeedEvidence() {
        NonConformity critical = NonConformity.create(
                UUID.randomUUID(), inspectionId, null, null, "Critical", "Description", NonConformitySeverity.CRITICAL,
                technicianId, Instant.now(), Instant.now()
        );
        when(repository.findAllByInspectionIdAndSeverity(inspectionId, NonConformitySeverity.CRITICAL))
                .thenReturn(List.of(critical));
        when(evidenceRepository.existsByNonConformityIdAndDeletedAtIsNull(critical.getId())).thenReturn(false);

        assertThat(service.hasCriticalWithoutEvidence(inspectionId)).isTrue();
    }

    private CreateNonConformityRequest request(UUID id, NonConformitySeverity severity, UUID responseId) {
        return new CreateNonConformityRequest(
                id, null, responseId, "Lacre rompido", "O lacre foi encontrado rompido no recebimento.",
                severity, Instant.parse("2026-11-23T10:00:00Z")
        );
    }
}
