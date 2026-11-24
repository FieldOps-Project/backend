package com.fieldops.inspection.application;

import com.fieldops.audit.infrastructure.persistence.AuditEvent;
import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionHistoryServiceTest {

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
    private final Authentication authentication = mock(Authentication.class);
    private final InspectionHistoryService service = new InspectionHistoryService(
            inspectionRepository, auditEventRepository, userRepository, userResolver
    );

    private final UUID inspectionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void technicianCannotReadTheHistoryOfAnotherTechniciansInspection() {
        User technician = user(UserRole.TECHNICIAN, "Tecnico");
        when(userResolver.requireUser(authentication)).thenReturn(technician);
        when(inspectionRepository.existsByIdAndTechnicianId(inspectionId, technician.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.findByInspectionId(inspectionId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(auditEventRepository, never()).findAllByInspectionIdOrderByOccurredAtAscIdAsc(any());
    }

    @Test
    void timelineKeepsOrderAndResolvesAuthorsInOneBatch() {
        User supervisor = user(UserRole.SUPERVISOR, "Supervisor Joao");
        User technician = user(UserRole.TECHNICIAN, "Tecnico Ana");
        when(userResolver.requireUser(authentication)).thenReturn(supervisor);
        when(inspectionRepository.existsById(inspectionId)).thenReturn(true);
        Instant deviceTime = Instant.parse("2026-11-20T10:00:00Z");
        AuditEvent started = event(technician.getId(), "INSPECTION_STARTED", deviceTime);
        AuditEvent rejected = event(supervisor.getId(), "INSPECTION_REJECTED", null);
        AuditEvent restarted = event(technician.getId(), "INSPECTION_REOPENED", null);
        when(auditEventRepository.findAllByInspectionIdOrderByOccurredAtAscIdAsc(inspectionId))
                .thenReturn(List.of(started, rejected, restarted));
        when(userRepository.findAllById(Set.of(technician.getId(), supervisor.getId())))
                .thenReturn(List.of(technician, supervisor));

        List<InspectionHistoryEntry> timeline = service.findByInspectionId(inspectionId);

        assertThat(timeline).extracting(InspectionHistoryEntry::action)
                .containsExactly("INSPECTION_STARTED", "INSPECTION_REJECTED", "INSPECTION_REOPENED");
        assertThat(timeline).extracting(InspectionHistoryEntry::actorName)
                .containsExactly("Tecnico Ana", "Supervisor Joao", "Tecnico Ana");
        assertThat(timeline.get(0).deviceOccurredAt()).isEqualTo(deviceTime);
        verify(userRepository).findAllById(Set.of(technician.getId(), supervisor.getId()));
    }

    private AuditEvent event(UUID actorId, String action, Instant deviceTime) {
        return AuditEvent.create(actorId, action, "INSPECTION", inspectionId, inspectionId, null, null,
                Instant.now(), deviceTime, "req", null);
    }

    private User user(UserRole role, String name) {
        UUID id = UUID.randomUUID();
        User user = User.create(name, id + "@example.com", "hash", role, UserStatus.ACTIVE, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
