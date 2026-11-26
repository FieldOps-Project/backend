package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.service.InspectionStateMachine;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.notification.application.InspectionAssignedEvent;
import com.fieldops.inspection.presentation.dto.InspectionLocationRequest;
import com.fieldops.inspection.presentation.dto.StartInspectionRequest;
import com.fieldops.inspection.presentation.dto.SubmitInspectionRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.Clock;
import java.math.BigDecimal;
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
class InspectionTransitionServiceTest {

    @Mock
    private InspectionRepository inspectionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private InspectionTransitionService transitionService;
    private Inspection inspection;
    private UUID inspectionId;
    private UUID technicianId;
    private UUID supervisorId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        transitionService = new InspectionTransitionService(
                inspectionRepository,
                userRepository,
                new InspectionStateMachine(),
                auditEventService,
                authenticatedUserResolver,
                inspectionIdValue -> List.of(),
                inspectionIdValue -> false,
                Clock.systemUTC(),
                applicationEventPublisher
        );

        InspectionTemplate template = InspectionTemplate.create(
                "Extintores", null, "Incendio", supervisorId = UUID.randomUUID()
        );
        InspectionTemplateVersion version = InspectionTemplateVersion.createPublished(
                template, 1, "Extintores", null, supervisorId
        );
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());

        technicianId = UUID.randomUUID();
        inspectionId = UUID.randomUUID();
        inspection = Inspection.create(
                version,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                technicianId,
                supervisorId,
                supervisorId,
                "Extintores",
                null,
                null,
                Instant.parse("2026-11-12T13:00:00Z")
        );
        ReflectionTestUtils.setField(inspection, "id", inspectionId);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignsDraftInspectionToAnActiveTechnicianAndAuditsTheStateChange() {
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.DRAFT);
        ReflectionTestUtils.setField(inspection, "technicianId", null);
        User technician = user(technicianId, UserRole.TECHNICIAN, UserStatus.ACTIVE);
        when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));
        when(userRepository.findById(technicianId)).thenReturn(Optional.of(technician));
        when(inspectionRepository.saveAndFlush(any(Inspection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InspectionTransitionResult result = transitionService.assign(inspectionId, technicianId);

        assertThat(result.previousStatus()).isEqualTo(InspectionStatus.DRAFT);
        assertThat(result.inspection().getStatus()).isEqualTo(InspectionStatus.ASSIGNED);
        assertThat(result.inspection().getTechnicianId()).isEqualTo(technicianId);
        verify(auditEventService).recordInspectionEvent(
                eq("INSPECTION_ASSIGNED"),
                eq("INSPECTION"),
                eq(inspectionId),
                eq(inspectionId),
                eq(null),
                eq("status=DRAFT;technicianId=null;canceledBy=null;canceledAt=null;canceledReason=null"),
                eq("status=ASSIGNED;technicianId=" + technicianId + ";canceledBy=null;canceledAt=null;canceledReason=null")
        );
        verify(applicationEventPublisher).publishEvent(new InspectionAssignedEvent(inspectionId, technicianId));
    }

    @Test
    void cancelsAnAssignedInspectionWithActorTimestampReasonAndAudit() {
        supervisorId = UUID.randomUUID();
        authentication = new TestingAuthenticationToken("supervisor", "password");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(supervisorId);
        when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));
        when(inspectionRepository.saveAndFlush(any(Inspection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InspectionTransitionResult result = transitionService.cancel(inspectionId, "Client site unavailable");

        assertThat(result.previousStatus()).isEqualTo(InspectionStatus.ASSIGNED);
        assertThat(result.inspection().getStatus()).isEqualTo(InspectionStatus.CANCELED);
        assertThat(result.inspection().getCanceledBy()).isEqualTo(supervisorId);
        assertThat(result.inspection().getCanceledAt()).isNotNull();
        assertThat(result.inspection().getCanceledReason()).isEqualTo("Client site unavailable");
        verify(auditEventService).recordInspectionEvent(
                eq("INSPECTION_CANCELED"),
                eq("INSPECTION"),
                eq(inspectionId),
                eq(inspectionId),
                eq(null),
                eq("status=ASSIGNED;technicianId=" + technicianId + ";canceledBy=null;canceledAt=null;canceledReason=null"),
                org.mockito.ArgumentMatchers.argThat(value -> value.startsWith(
                        "status=CANCELED;technicianId=" + technicianId
                ) && value.contains("canceledBy=" + supervisorId)
                        && value.contains("canceledReason=Client site unavailable"))
        );
    }

    @Test
    void rejectsAssignmentToInactiveOrWrongRoleUser() {
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.DRAFT);
        when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));
        when(userRepository.findById(technicianId)).thenReturn(Optional.of(
                user(technicianId, UserRole.SUPERVISOR, UserStatus.INACTIVE)
        ));

        assertThatThrownBy(() -> transitionService.assign(inspectionId, technicianId))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TECHNICIAN_NOT_ELIGIBLE");

        verify(inspectionRepository, never()).saveAndFlush(any(Inspection.class));
        verify(auditEventService, never()).recordInspectionEvent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void technicianExplicitlyReopensARejectedInspection() {
        authenticateTechnician();
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.REJECTED);
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        when(inspectionRepository.saveAndFlush(any(Inspection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InspectionTransitionResult result = transitionService.reopen(inspectionId);

        assertThat(result.previousStatus()).isEqualTo(InspectionStatus.REJECTED);
        assertThat(result.inspection().getStatus()).isEqualTo(InspectionStatus.IN_PROGRESS);
        verify(auditEventService).recordInspectionEvent(
                eq("INSPECTION_REOPENED"), eq("INSPECTION"), eq(inspectionId), eq(inspectionId), eq(null), any(), any()
        );
    }

    @Test
    void approvedInspectionCannotBeReopened() {
        authenticateTechnician();
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.APPROVED);
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));

        assertThatThrownBy(() -> transitionService.reopen(inspectionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_INVALID_TRANSITION");

        verify(inspectionRepository, never()).saveAndFlush(any(Inspection.class));
    }

    @Test
    void rejectsCancellationFromApprovedStateWithConflict() {
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.APPROVED);
        when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));

        assertThatThrownBy(() -> transitionService.cancel(inspectionId, "Duplicate request"))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_INVALID_TRANSITION");

        verify(inspectionRepository, never()).saveAndFlush(any(Inspection.class));
    }

    @Test
    void rejectsCancellationWithoutAReasonAsAnInvalidRequest() {
        assertThatThrownBy(() -> transitionService.cancel(inspectionId, "  "))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_CANCEL_REASON_REQUIRED");
    }

    @Test
    void startsOnlyTheAssignedTechniciansInspectionAndRecordsBothTimesAndPosition() {
        authenticateTechnician();
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        when(inspectionRepository.saveAndFlush(any(Inspection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Instant deviceTime = Instant.parse("2026-11-23T10:02:00Z");
        InspectionLocationRequest location = new InspectionLocationRequest(
                new BigDecimal("-23.5"), new BigDecimal("-47.4"), new BigDecimal("12.5"),
                Instant.parse("2026-11-23T10:02:03Z")
        );
        transitionService = transitionService(inspectionIdValue -> List.of());

        InspectionTransitionResult result = transitionService.start(
                inspectionId, new StartInspectionRequest(deviceTime, location)
        );

        assertThat(result.previousStatus()).isEqualTo(InspectionStatus.ASSIGNED);
        assertThat(result.inspection().getStatus()).isEqualTo(InspectionStatus.IN_PROGRESS);
        assertThat(result.inspection().getStartedAtDevice()).isEqualTo(deviceTime);
        assertThat(result.inspection().getStartedAtServer()).isEqualTo(Instant.parse("2026-11-23T12:00:00Z"));
        assertThat(result.inspection().getStartPosition().getAccuracyMeters()).isEqualByComparingTo("12.5");
        verify(auditEventService).recordInspectionEvent(
                eq("INSPECTION_STARTED"), eq("INSPECTION"), eq(inspectionId), eq(inspectionId),
                eq(result.inspection().getStartedAtDevice()),
                eq("status=ASSIGNED;technicianId=" + technicianId
                        + ";startedAtDevice=null;startedAtServer=null;completedAtDevice=null;submittedAtServer=null"),
                org.mockito.ArgumentMatchers.contains("status=IN_PROGRESS")
        );
    }

    @Test
    void startReturnsNotFoundWhenInspectionBelongsToAnotherTechnician() {
        authenticateTechnician();
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.empty());
        transitionService = transitionService(inspectionIdValue -> List.of());

        assertThatThrownBy(() -> transitionService.start(
                inspectionId, new StartInspectionRequest(Instant.now(), null)
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startRejectsCanceledInspectionWithConflict() {
        authenticateTechnician();
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.CANCELED);
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        transitionService = transitionService(inspectionIdValue -> List.of());

        assertThatThrownBy(() -> transitionService.start(
                inspectionId, new StartInspectionRequest(Instant.now(), null)
        )).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_INVALID_TRANSITION");
        verify(inspectionRepository, never()).saveAndFlush(any(Inspection.class));
    }

    @Test
    void submitListsRequiredItemsAndDoesNotChangeTheInspection() {
        authenticateTechnician();
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.IN_PROGRESS);
        InspectionItemSnapshot item = snapshot(true, null);
        ReflectionTestUtils.setField(inspection, "itemSnapshots", new java.util.ArrayList<>(List.of(item)));
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        transitionService = transitionService(inspectionIdValue -> List.of());

        assertThatThrownBy(() -> transitionService.submit(
                inspectionId, new SubmitInspectionRequest(Instant.now(), null)
        )).isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_REQUIRED_ITEMS_MISSING")
                .satisfies(error -> assertThat(((BusinessRuleException) error).getViolations())
                        .singleElement().extracting("message")
                        .isEqualTo("A response is required"));

        verify(inspectionRepository, never()).saveAndFlush(any(Inspection.class));
        verify(auditEventService, never()).recordInspectionEvent(eq("INSPECTION_SUBMITTED"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitRequiresObservationAndEvidenceForNonConformingResponses() {
        authenticateTechnician();
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.IN_PROGRESS);
        InspectionItemSnapshot item = snapshot(false,
                "{\"observationRequiredOnFailure\":true,\"evidenceRequiredOnFailure\":true}");
        ReflectionTestUtils.setField(inspection, "itemSnapshots", new java.util.ArrayList<>(List.of(item)));
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        transitionService = transitionService(inspectionIdValue -> List.of(
                new InspectionExecutionAnswer(item.getId(), true, "NON_CONFORMING", null, false)
        ));

        assertThatThrownBy(() -> transitionService.submit(
                inspectionId, new SubmitInspectionRequest(Instant.now(), null)
        )).isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_REQUIRED_ITEMS_MISSING");
        verify(inspectionRepository, never()).saveAndFlush(any(Inspection.class));
    }

    @Test
    void submitChangesStateRecordsServerTimeAndAuditsAValidResponse() {
        authenticateTechnician();
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.IN_PROGRESS);
        InspectionItemSnapshot item = snapshot(true, null);
        ReflectionTestUtils.setField(inspection, "itemSnapshots", new java.util.ArrayList<>(List.of(item)));
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        when(inspectionRepository.saveAndFlush(any(Inspection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        transitionService = transitionService(inspectionIdValue -> List.of(
                new InspectionExecutionAnswer(item.getId(), true, "CONFORMING", null, false)
        ));

        Instant deviceTime = Instant.parse("2026-11-23T10:48:00Z");
        InspectionTransitionResult result = transitionService.submit(
                inspectionId,
                new SubmitInspectionRequest(deviceTime, new InspectionLocationRequest(
                        new BigDecimal("-23.5"), new BigDecimal("-47.4"), null,
                        Instant.parse("2026-11-23T10:48:03Z")
                ))
        );

        assertThat(result.previousStatus()).isEqualTo(InspectionStatus.IN_PROGRESS);
        assertThat(result.inspection().getStatus()).isEqualTo(InspectionStatus.SUBMITTED);
        assertThat(result.inspection().getCompletedAtDevice()).isEqualTo(deviceTime);
        assertThat(result.inspection().getSubmittedAtServer()).isEqualTo(Instant.parse("2026-11-23T12:00:00Z"));
        assertThat(result.inspection().getCompletionPosition()).isNotNull();
        verify(auditEventService).recordInspectionEvent(
                eq("INSPECTION_SUBMITTED"), eq("INSPECTION"), eq(inspectionId), eq(inspectionId),
                eq(inspection.getCompletedAtDevice()),
                org.mockito.ArgumentMatchers.contains("status=IN_PROGRESS"),
                org.mockito.ArgumentMatchers.contains("status=SUBMITTED")
        );
        verify(auditEventService).recordInspectionEvent(
                eq("INSPECTION_COMPLETED"), eq("INSPECTION"), eq(inspectionId), eq(inspectionId),
                eq(inspection.getCompletedAtDevice()),
                org.mockito.ArgumentMatchers.contains("status=IN_PROGRESS"),
                org.mockito.ArgumentMatchers.contains("status=SUBMITTED")
        );
    }

    @Test
    void submitBlocksCriticalNonConformityWithoutEvidenceAtConsolidation() {
        authenticateTechnician();
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.IN_PROGRESS);
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        transitionService = transitionService(
                inspectionIdValue -> List.of(),
                inspectionIdValue -> true
        );

        assertThatThrownBy(() -> transitionService.submit(
                inspectionId, new SubmitInspectionRequest(Instant.now(), null)
        )).isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_CRITICAL_NON_CONFORMITY_EVIDENCE_MISSING");
        verify(inspectionRepository, never()).saveAndFlush(any(Inspection.class));
    }

    private void authenticateTechnician() {
        authentication = new TestingAuthenticationToken("technician", "password");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(technicianId);
    }

    private InspectionTransitionService transitionService(InspectionResponseReader responseReader) {
        return transitionService(responseReader, inspectionIdValue -> false);
    }

    private InspectionTransitionService transitionService(
            InspectionResponseReader responseReader,
            InspectionNonConformityReader nonConformityReader
    ) {
        return new InspectionTransitionService(
                inspectionRepository,
                userRepository,
                new InspectionStateMachine(),
                auditEventService,
                authenticatedUserResolver,
                responseReader,
                nonConformityReader,
                Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), java.time.ZoneOffset.UTC)
        );
    }

    private InspectionItemSnapshot snapshot(boolean required, String rules) {
        InspectionItemSnapshot item = org.mockito.Mockito.mock(InspectionItemSnapshot.class);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.isRequired()).thenReturn(required);
        if (rules != null) {
            when(item.getRulesJson()).thenReturn(rules);
        }
        return item;
    }

    private User user(UUID id, UserRole role, UserStatus status) {
        User user = User.create("FieldOps user", id + "@example.com", "hash", role, status, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
