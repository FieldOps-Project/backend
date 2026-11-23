package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.service.InspectionStateMachine;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
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
                authenticatedUserResolver
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
        verify(auditEventService).recordEvent(
                eq("INSPECTION_ASSIGNED"),
                eq("INSPECTION"),
                eq(inspectionId),
                eq("status=DRAFT;technicianId=null;canceledBy=null;canceledAt=null;canceledReason=null"),
                eq("status=ASSIGNED;technicianId=" + technicianId + ";canceledBy=null;canceledAt=null;canceledReason=null")
        );
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
        verify(auditEventService).recordEvent(
                eq("INSPECTION_CANCELED"),
                eq("INSPECTION"),
                eq(inspectionId),
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
        verify(auditEventService, never()).recordEvent(any(), any(), any(), any(), any());
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

    private User user(UUID id, UserRole role, UserStatus status) {
        User user = User.create("FieldOps user", id + "@example.com", "hash", role, status, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
