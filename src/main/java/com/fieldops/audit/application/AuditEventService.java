package com.fieldops.audit.application;

import com.fieldops.audit.infrastructure.persistence.AuditEvent;
import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.shared.infrastructure.filter.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Single write path of the audit trail (RN-086). Callers pass only allow-listed,
 * non-sensitive values (RN-007); the request id comes from the request MDC and the
 * device id from the synchronization MDC.
 */
@Service
public class AuditEventService {

    /** MDC key set by the synchronization pipeline while it applies a device operation. */
    public static final String DEVICE_ID_MDC_KEY = "deviceId";

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
        recordEvent(action, USER_ENTITY, userId, beforeValues, afterValues);
    }

    @Transactional
    public void recordEvent(
            String action,
            String entityType,
            UUID entityId,
            String beforeValues,
            String afterValues
    ) {
        recordInspectionEvent(action, entityType, entityId, null, null, beforeValues, afterValues);
    }

    @Transactional
    public void recordInspectionEvent(
            String action,
            String entityType,
            UUID entityId,
            UUID inspectionId,
            Instant deviceOccurredAt,
            String beforeValues,
            String afterValues
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID actorUserId = authenticatedUserResolver.requireUserId(authentication);
        String deviceId = MDC.get(DEVICE_ID_MDC_KEY);

        auditEventRepository.save(AuditEvent.create(
                actorUserId,
                action,
                entityType,
                entityId,
                inspectionId,
                beforeValues,
                afterValues,
                Instant.now(),
                deviceOccurredAt,
                MDC.get(RequestIdFilter.MDC_KEY),
                deviceId == null ? null : UUID.fromString(deviceId)
        ));
    }
}
