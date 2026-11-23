package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.inspection.domain.model.FieldPosition;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionAction;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.service.InspectionStateMachine;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.presentation.dto.InspectionLocationRequest;
import com.fieldops.inspection.presentation.dto.StartInspectionRequest;
import com.fieldops.inspection.presentation.dto.SubmitInspectionRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.domain.exception.Violation;
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

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final InspectionResponseReader responseReader;
    private final InspectionNonConformityReader nonConformityReader;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public InspectionTransitionService(
            ObjectProvider<InspectionRepository> inspectionRepositoryProvider,
            ObjectProvider<InspectionResponseReader> responseReaderProvider,
            ObjectProvider<InspectionNonConformityReader> nonConformityReaderProvider,
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
        InspectionResponseReader availableReader = responseReaderProvider.getIfAvailable();
        this.responseReader = availableReader == null ? inspectionId -> List.of() : availableReader;
        InspectionNonConformityReader availableNonConformityReader = nonConformityReaderProvider.getIfAvailable();
        this.nonConformityReader = availableNonConformityReader == null
                ? inspectionId -> false : availableNonConformityReader;
        this.clock = Clock.systemUTC();
        this.objectMapper = new ObjectMapper();
    }

    /** Constructor kept convenient for unit tests that do not start a Spring context. */
    public InspectionTransitionService(
            InspectionRepository inspectionRepository,
            UserRepository userRepository,
            InspectionStateMachine stateMachine,
            AuditEventService auditEventService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this(
                inspectionRepository,
                userRepository,
                stateMachine,
                auditEventService,
                authenticatedUserResolver,
                inspectionId -> List.of(),
                inspectionId -> false,
                Clock.systemUTC()
        );
    }

    InspectionTransitionService(
            InspectionRepository inspectionRepository,
            UserRepository userRepository,
            InspectionStateMachine stateMachine,
            AuditEventService auditEventService,
            AuthenticatedUserResolver authenticatedUserResolver,
            InspectionResponseReader responseReader,
            Clock clock
    ) {
        this(
                inspectionRepository,
                userRepository,
                stateMachine,
                auditEventService,
                authenticatedUserResolver,
                responseReader,
                inspectionId -> false,
                clock
        );
    }

    InspectionTransitionService(
            InspectionRepository inspectionRepository,
            UserRepository userRepository,
            InspectionStateMachine stateMachine,
            AuditEventService auditEventService,
            AuthenticatedUserResolver authenticatedUserResolver,
            InspectionResponseReader responseReader,
            InspectionNonConformityReader nonConformityReader,
            Clock clock
    ) {
        this.inspectionRepositoryProvider = null;
        this.directInspectionRepository = inspectionRepository;
        this.userRepository = userRepository;
        this.stateMachine = stateMachine;
        this.auditEventService = auditEventService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.responseReader = responseReader;
        this.nonConformityReader = nonConformityReader;
        this.clock = clock;
        this.objectMapper = new ObjectMapper();
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

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionTransitionResult start(UUID inspectionId, StartInspectionRequest request) {
        Inspection inspection = findForTechnician(inspectionId);
        stateMachine.assertTransitionValid(inspection.getStatus(), InspectionAction.START);

        String before = executionAuditState(inspection);
        InspectionStatus previousStatus = inspection.getStatus();
        inspection.start(
                request.startedAtDevice(),
                clock.instant(),
                toFieldPosition(request.location())
        );
        Inspection saved = inspectionRepository().saveAndFlush(inspection);
        auditEventService.recordEvent(
                "INSPECTION_STARTED",
                INSPECTION_ENTITY,
                saved.getId(),
                before,
                executionAuditState(saved)
        );
        return new InspectionTransitionResult(saved, previousStatus);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionTransitionResult submit(UUID inspectionId, SubmitInspectionRequest request) {
        Inspection inspection = findForTechnician(inspectionId);
        stateMachine.assertTransitionValid(inspection.getStatus(), InspectionAction.SUBMIT);
        validateSubmission(inspection);
        if (nonConformityReader.hasCriticalWithoutEvidence(inspection.getId())) {
            throw new BusinessRuleException(
                    "INSPECTION_CRITICAL_NON_CONFORMITY_EVIDENCE_MISSING",
                    "Critical non-conformities require at least one active evidence before submission",
                    List.of(new Violation("nonConformities", "Every CRITICAL non-conformity must have evidence"))
            );
        }

        String before = executionAuditState(inspection);
        InspectionStatus previousStatus = inspection.getStatus();
        inspection.submit(
                request.completedAtDevice(),
                clock.instant(),
                toFieldPosition(request.location())
        );
        Inspection saved = inspectionRepository().saveAndFlush(inspection);
        auditEventService.recordEvent(
                "INSPECTION_COMPLETED",
                INSPECTION_ENTITY,
                saved.getId(),
                before,
                executionAuditState(saved)
        );
        auditEventService.recordEvent(
                "INSPECTION_SUBMITTED",
                INSPECTION_ENTITY,
                saved.getId(),
                before,
                executionAuditState(saved)
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

    private Inspection findForTechnician(UUID inspectionId) {
        UUID technicianId = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );
        return inspectionRepository().findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
    }

    private void validateSubmission(Inspection inspection) {
        Map<UUID, InspectionExecutionAnswer> answers = new HashMap<>();
        responseReader.findByInspectionId(inspection.getId())
                .forEach(answer -> answers.put(answer.itemId(), answer));

        List<Violation> violations = inspection.getItemSnapshots().stream()
                .map(snapshot -> validateItem(snapshot, answers.get(snapshot.getId()), inspection))
                .flatMap(java.util.Optional::stream)
                .toList();
        if (!violations.isEmpty()) {
            throw new BusinessRuleException(
                    "INSPECTION_REQUIRED_ITEMS_MISSING",
                    "The inspection has incomplete required responses",
                    violations
            );
        }
    }

    private java.util.Optional<Violation> validateItem(
            InspectionItemSnapshot snapshot,
            InspectionExecutionAnswer answer,
            Inspection inspection
    ) {
        if (snapshot.isRequired() && (answer == null || !answer.answered())) {
            return java.util.Optional.of(violation(snapshot, "A response is required"));
        }
        if (answer == null || !answer.isNonConforming()) {
            return java.util.Optional.empty();
        }

        JsonNode rules = readRules(snapshot.getRulesJson());
        if (rules.path("observationRequiredOnFailure").asBoolean(false)
                && isBlank(answer.observation())) {
            return java.util.Optional.of(violation(snapshot, "An observation is required for a non-conforming response"));
        }
        if (rules.path("evidenceRequiredOnFailure").asBoolean(false)
                && !answer.hasEvidence()) {
            return java.util.Optional.of(violation(snapshot, "Evidence is required for a non-conforming response"));
        }
        if (inspection.getPriority() == com.fieldops.inspection.domain.model.InspectionPriority.CRITICAL
                && (isBlank(answer.observation()) || !answer.hasEvidence())) {
            return java.util.Optional.of(violation(snapshot, "Critical non-conforming responses require observation and evidence"));
        }
        return java.util.Optional.empty();
    }

    private Violation violation(InspectionItemSnapshot snapshot, String message) {
        return new Violation("items[" + snapshot.getId() + "]", message);
    }

    private JsonNode readRules(String rulesJson) {
        try {
            return objectMapper.readTree(rulesJson == null ? "{}" : rulesJson);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored inspection checklist rules are invalid", ex);
        }
    }

    private FieldPosition toFieldPosition(InspectionLocationRequest location) {
        if (location == null) {
            return null;
        }
        try {
            return FieldPosition.of(
                    location.latitude(),
                    location.longitude(),
                    location.accuracy(),
                    location.capturedAt()
            );
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INSPECTION_LOCATION_INVALID", ex.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    private String executionAuditState(Inspection inspection) {
        return "status=" + inspection.getStatus()
                + ";technicianId=" + inspection.getTechnicianId()
                + ";startedAtDevice=" + inspection.getStartedAtDevice()
                + ";startedAtServer=" + inspection.getStartedAtServer()
                + ";completedAtDevice=" + inspection.getCompletedAtDevice()
                + ";submittedAtServer=" + inspection.getSubmittedAtServer();
    }

}
