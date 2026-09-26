package com.fieldops.audit.application;

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
    }
}
