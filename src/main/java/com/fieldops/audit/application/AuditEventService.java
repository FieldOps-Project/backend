package com.fieldops.audit.application;

import com.fieldops.audit.infrastructure.persistence.AuditEvent;
import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditEventService {

    private static final String USER_ENTITY = "USER";

    private final AuditEventRepository auditEventRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public AuditEventService(
            AuditEventRepository auditEventRepository,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.auditEventRepository = auditEventRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Transactional
    public void recordUserEvent(
            String action,
            UUID userId,
            String beforeValues,
            String afterValues
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID actorUserId = authenticatedUserResolver.requireUserId(authentication);

        auditEventRepository.save(AuditEvent.create(
                actorUserId,
                action,
                USER_ENTITY,
                userId,
                beforeValues,
                afterValues,
                Instant.now()
        ));
    }
}
