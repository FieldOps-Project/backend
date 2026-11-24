package com.fieldops.audit.application;

import java.time.Instant;
import org.slf4j.MDC;
import com.fieldops.shared.infrastructure.filter.RequestIdFilter;
import com.fieldops.audit.infrastructure.persistence.AuditEvent;
import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsActorAndBeforeAfterValuesFromTheAuthenticatedContext() {
        UUID actorId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "jwt-principal",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(actorId);

        AuditEventService service = new AuditEventService(auditEventRepository, authenticatedUserResolver);
        service.recordUserEvent("USER_UPDATED", entityId, "before", "after");

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getActorUserId()).isEqualTo(actorId);
        assertThat(event.getEntityId()).isEqualTo(entityId);
        assertThat(event.getAction()).isEqualTo("USER_UPDATED");
        assertThat(event.getBeforeValues()).isEqualTo("before");
        assertThat(event.getAfterValues()).isEqualTo("after");
        assertThat(event.getBeforeValues()).doesNotContain("password");
        assertThat(event.getAfterValues()).doesNotContain("password");
        assertThat(event.getEntityType()).isEqualTo("USER");
    }

    @Test
    void recordsEventsForOtherEntityTypes() {
        UUID actorId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "jwt-principal",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(actorId);

        AuditEventService service = new AuditEventService(auditEventRepository, authenticatedUserResolver);
        service.recordEvent("TEMPLATE_VERSION_PUBLISHED", "INSPECTION_TEMPLATE_VERSION", versionId, null, "{\"versionNumber\":1}");

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getActorUserId()).isEqualTo(actorId);
        assertThat(event.getEntityType()).isEqualTo("INSPECTION_TEMPLATE_VERSION");
        assertThat(event.getEntityId()).isEqualTo(versionId);
        assertThat(event.getBeforeValues()).isNull();
        assertThat(event.getAfterValues()).isEqualTo("{\"versionNumber\":1}");
    }

    @Test
    void inspectionEventsCarryInspectionDeviceTimeAndCorrelationIds() {
        UUID actorId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Instant deviceTime = Instant.parse("2026-11-20T10:00:00Z");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "jwt-principal",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(actorId);
        MDC.put(RequestIdFilter.MDC_KEY, "req-123");
        MDC.put(AuditEventService.DEVICE_ID_MDC_KEY, deviceId.toString());

        try {
            AuditEventService service = new AuditEventService(auditEventRepository, authenticatedUserResolver);
            service.recordInspectionEvent(
                    "INSPECTION_STARTED", "INSPECTION", inspectionId, inspectionId, deviceTime, "before", "after"
            );
        } finally {
            MDC.clear();
        }

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getInspectionId()).isEqualTo(inspectionId);
        assertThat(event.getDeviceOccurredAt()).isEqualTo(deviceTime);
        assertThat(event.getRequestId()).isEqualTo("req-123");
        assertThat(event.getDeviceId()).isEqualTo(deviceId);
        assertThat(event.getOccurredAt()).isNotNull();
    }
}
