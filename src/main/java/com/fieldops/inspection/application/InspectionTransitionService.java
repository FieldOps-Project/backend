package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionAction;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.service.InspectionStateMachine;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InspectionTransitionService {

    private static final String INSPECTION_ENTITY = "INSPECTION";

    private final ObjectProvider<InspectionRepository> inspectionRepositoryProvider;
    private final InspectionRepository directInspectionRepository;
    private final UserRepository userRepository;
    private final InspectionStateMachine stateMachine;
    private final AuditEventService auditEventService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Autowired
    public InspectionTransitionService(
            ObjectProvider<InspectionRepository> inspectionRepositoryProvider,
            UserRepository userRepository,
            InspectionStateMachine stateMachine,
            AuditEventService auditEventService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepositoryProvider = inspectionRepositoryProvider;
        this.directInspectionRepository = null;
        this.userRepository = userRepository;
        this.stateMachine = stateMachine;
        this.auditEventService = auditEventService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    /** Constructor kept convenient for unit tests that do not start a Spring context. */
    public InspectionTransitionService(
            InspectionRepository inspectionRepository,
            UserRepository userRepository,
            InspectionStateMachine stateMachine,
            AuditEventService auditEventService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepositoryProvider = null;
        this.directInspectionRepository = inspectionRepository;
        this.userRepository = userRepository;
        this.stateMachine = stateMachine;
        this.auditEventService = auditEventService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public InspectionTransitionResult assign(UUID inspectionId, UUID technicianId) {
        Inspection inspection = findRequired(inspectionId);
        stateMachine.assertTransitionValid(inspection.getStatus(), InspectionAction.ASSIGN);
        User technician = findTechnician(technicianId);

        String before = auditState(inspection);
        InspectionStatus previousStatus = inspection.getStatus();
        inspection.assign(technician.getId());
        Inspection saved = inspectionRepository().saveAndFlush(inspection);
        auditEventService.recordEvent(
                "INSPECTION_ASSIGNED",
                INSPECTION_ENTITY,
                saved.getId(),
                before,
                auditState(saved)
        );
        return new InspectionTransitionResult(saved, previousStatus);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public InspectionTransitionResult cancel(UUID inspectionId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException(
                    "INSPECTION_CANCEL_REASON_REQUIRED",
                    "Cancellation reason must not be blank"
            );
        }
        Inspection inspection = findRequired(inspectionId);
        stateMachine.assertTransitionValid(inspection.getStatus(), InspectionAction.CANCEL);

        UUID actorUserId = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );
        String before = auditState(inspection);
        InspectionStatus previousStatus = inspection.getStatus();
        inspection.cancel(actorUserId, reason.trim(), Instant.now());
        Inspection saved = inspectionRepository().saveAndFlush(inspection);
        auditEventService.recordEvent(
                "INSPECTION_CANCELED",
                INSPECTION_ENTITY,
                saved.getId(),
                before,
                auditState(saved)
        );
        return new InspectionTransitionResult(saved, previousStatus);
    }

    private User findTechnician(UUID technicianId) {
        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", technicianId));
        if (technician.getStatus() != UserStatus.ACTIVE || technician.getRole() != UserRole.TECHNICIAN) {
            throw new BusinessRuleException(
                    "TECHNICIAN_NOT_ELIGIBLE",
                    "Only active users with the TECHNICIAN role can be assigned to an inspection"
            );
        }
        return technician;
    }

    private Inspection findRequired(UUID inspectionId) {
        return inspectionRepository().findById(inspectionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
    }

    private InspectionRepository inspectionRepository() {
        if (directInspectionRepository != null) {
            return directInspectionRepository;
        }
        return inspectionRepositoryProvider.getObject();
    }

    private String auditState(Inspection inspection) {
        return "status=" + inspection.getStatus()
                + ";technicianId=" + inspection.getTechnicianId()
                + ";canceledBy=" + inspection.getCanceledBy()
                + ";canceledAt=" + inspection.getCanceledAt()
                + ";canceledReason=" + inspection.getCanceledReason();
    }

}
