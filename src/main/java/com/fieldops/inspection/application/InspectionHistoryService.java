package com.fieldops.inspection.application;

import com.fieldops.audit.infrastructure.persistence.AuditEvent;
import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InspectionHistoryService {

    private final ObjectProvider<InspectionRepository> inspectionRepositoryProvider;
    private final InspectionRepository directInspectionRepository;
    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Autowired
    public InspectionHistoryService(
            ObjectProvider<InspectionRepository> inspectionRepositoryProvider,
            AuditEventRepository auditEventRepository,
            UserRepository userRepository,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepositoryProvider = inspectionRepositoryProvider;
        this.directInspectionRepository = null;
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    InspectionHistoryService(
            InspectionRepository inspectionRepository,
            AuditEventRepository auditEventRepository,
            UserRepository userRepository,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepositoryProvider = null;
        this.directInspectionRepository = inspectionRepository;
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    /** Timeline ordered by server time; authors are loaded in one batch to avoid N+1. */
    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_HISTORY_READ)
    public List<InspectionHistoryEntry> findByInspectionId(UUID inspectionId) {
        assertReadable(inspectionId);
        List<AuditEvent> events = auditEventRepository.findAllByInspectionIdOrderByOccurredAtAscIdAsc(inspectionId);
        Set<UUID> actorIds = events.stream().map(AuditEvent::getActorUserId).collect(Collectors.toSet());
        Map<UUID, String> actorNames = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        return events.stream()
                .map(event -> new InspectionHistoryEntry(
                        event.getId(),
                        event.getAction(),
                        event.getEntityType(),
                        event.getEntityId(),
                        event.getActorUserId(),
                        actorNames.get(event.getActorUserId()),
                        event.getOccurredAt(),
                        event.getDeviceOccurredAt(),
                        event.getRequestId(),
                        event.getDeviceId(),
                        event.getBeforeValues(),
                        event.getAfterValues()
                ))
                .toList();
    }

    private void assertReadable(UUID inspectionId) {
        User user = authenticatedUserResolver.requireUser(SecurityContextHolder.getContext().getAuthentication());
        boolean readable = user.getRole() == UserRole.TECHNICIAN
                ? inspectionRepository().existsByIdAndTechnicianId(inspectionId, user.getId())
                : inspectionRepository().existsById(inspectionId);
        if (!readable) {
            throw ResourceNotFoundException.of("Inspection", inspectionId);
        }
    }

    private InspectionRepository inspectionRepository() {
        return directInspectionRepository != null
                ? directInspectionRepository : inspectionRepositoryProvider.getObject();
    }
}
