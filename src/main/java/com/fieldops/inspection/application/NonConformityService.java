package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.NonConformity;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.domain.model.NonConformityStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.inspection.presentation.dto.CreateNonConformityRequest;
import com.fieldops.inspection.presentation.dto.UpdateNonConformityRequest;
import com.fieldops.inspection.presentation.dto.UpdateNonConformityStatusRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.domain.exception.Violation;
import com.fieldops.shared.presentation.dto.PaginationDefaults;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnBean(NonConformityRepository.class)
public class NonConformityService implements InspectionNonConformityReader {

    private static final String ENTITY = "NON_CONFORMITY";

    private final NonConformityRepository repository;
    private final InspectionRepository inspectionRepository;
    private final InspectionResponseRepository responseRepository;
    private final InspectionEvidenceRepository evidenceRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final AuditEventService auditEventService;
    private final Clock clock;

    @Autowired
    public NonConformityService(
            NonConformityRepository repository,
            InspectionRepository inspectionRepository,
            InspectionResponseRepository responseRepository,
            InspectionEvidenceRepository evidenceRepository,
            AuthenticatedUserResolver authenticatedUserResolver,
            AuditEventService auditEventService
    ) {
        this.repository = repository;
        this.inspectionRepository = inspectionRepository;
        this.responseRepository = responseRepository;
        this.evidenceRepository = evidenceRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.auditEventService = auditEventService;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_NON_CONFORMITY_READ)
    public Page<NonConformity> findAll(
            UUID inspectionId,
            NonConformitySeverity severity,
            NonConformityStatus status,
            UUID clientId,
            int page,
            int size
    ) {
        Pageable pageable = PaginationDefaults.of(page, size, Sort.by(Sort.Direction.ASC, "serverReceivedAt"));
        Set<UUID> scopedInspectionIds = accessibleInspectionIds();
        if (scopedInspectionIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return repository.search(scopedInspectionIds, inspectionId, severity, status, clientId, pageable);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_NON_CONFORMITY_READ)
    public NonConformity findById(UUID id) {
        NonConformity nonConformity = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("NonConformity", id));
        loadReadable(nonConformity.getInspectionId());
        return nonConformity;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public NonConformity create(UUID inspectionId, CreateNonConformityRequest request) {
        Inspection inspection = loadForTechnician(inspectionId);
        assertEditable(inspection);
        NonConformity existing = repository.findById(request.id()).orElse(null);
        if (existing != null) {
            if (!existing.getInspectionId().equals(inspectionId)) {
                throw new ConflictException("NON_CONFORMITY_ID_REUSED",
                        "The non-conformity id is already linked to another inspection");
            }
            return existing;
        }

        validateItemLink(inspection, request.inspectionItemId());
        validateResponseLink(inspectionId, request.responseId());
        UUID actor = currentUserId();
        NonConformity saved = repository.saveAndFlush(NonConformity.create(
                request.id(), inspectionId, request.inspectionItemId(), request.responseId(), request.title(),
                request.description(), request.severity(), actor, request.createdAtDevice(), clock.instant()
        ));
        auditEventService.recordEvent("NON_CONFORMITY_CREATED", ENTITY, saved.getId(), null, auditState(saved));
        return saved;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public NonConformity update(UUID id, UpdateNonConformityRequest request) {
        NonConformity nonConformity = loadForUpdate(id);
        String before = auditState(nonConformity);
        nonConformity.updateDetails(request.title(), request.description(), request.severity());
        NonConformity saved = repository.saveAndFlush(nonConformity);
        auditEventService.recordEvent("NON_CONFORMITY_UPDATED", ENTITY, saved.getId(), before, auditState(saved));
        return saved;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public NonConformity changeStatus(UUID id, UpdateNonConformityStatusRequest request) {
        NonConformity nonConformity = loadForUpdate(id);
        if (request.status() != NonConformityStatus.OPEN) {
            throw new BusinessRuleException("NON_CONFORMITY_STATUS_UNSUPPORTED",
                    "Only the OPEN non-conformity status is available in the MVP");
        }
        return nonConformity;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCriticalWithoutEvidence(UUID inspectionId) {
        return repository.findAllByInspectionIdAndSeverity(inspectionId, NonConformitySeverity.CRITICAL)
                .stream()
                .anyMatch(value -> !evidenceRepository.existsByNonConformityIdAndDeletedAtIsNull(value.getId()));
    }

    private NonConformity loadForUpdate(UUID id) {
        NonConformity nonConformity = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("NonConformity", id));
        assertEditable(loadForTechnician(nonConformity.getInspectionId()));
        return nonConformity;
    }

    private Inspection loadForTechnician(UUID inspectionId) {
        return inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, currentUserId())
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
    }

    private void assertEditable(Inspection inspection) {
        if (inspection.getStatus() == InspectionStatus.APPROVED) {
            throw new BusinessRuleException(
                    "INSPECTION_APPROVED_READ_ONLY",
                    "Approved inspection data cannot be changed"
            );
        }
    }

    private Inspection loadReadable(UUID inspectionId) {
        User user = authenticatedUserResolver.requireUser(SecurityContextHolder.getContext().getAuthentication());
        if (user.getRole() == UserRole.TECHNICIAN) {
            return inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, user.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("NonConformity", inspectionId));
        }
        if (user.getRole() == UserRole.SUPERVISOR) {
            return inspectionRepository.findByIdAndSupervisorId(inspectionId, user.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("NonConformity", inspectionId));
        }
        throw ResourceNotFoundException.of("NonConformity", inspectionId);
    }

    private Set<UUID> accessibleInspectionIds() {
        User user = authenticatedUserResolver.requireUser(SecurityContextHolder.getContext().getAuthentication());
        List<Inspection> inspections = switch (user.getRole()) {
            case TECHNICIAN -> inspectionRepository.findAllByTechnicianId(user.getId());
            case SUPERVISOR -> inspectionRepository.findAllBySupervisorId(user.getId());
            default -> List.of();
        };
        return inspections.stream().map(Inspection::getId).collect(java.util.stream.Collectors.toSet());
    }

    private void validateItemLink(Inspection inspection, UUID inspectionItemId) {
        if (inspectionItemId != null && inspection.getItemSnapshots().stream()
                .noneMatch(item -> item.getId().equals(inspectionItemId))) {
            throw new BusinessRuleException("NON_CONFORMITY_ITEM_NOT_IN_INSPECTION",
                    "The item does not belong to the inspection",
                    List.of(new Violation("inspectionItemId", "The item is not part of this inspection")));
        }
    }

    private void validateResponseLink(UUID inspectionId, UUID responseId) {
        if (responseId != null && responseRepository.findByIdAndInspectionId(responseId, inspectionId).isEmpty()) {
            throw new BusinessRuleException("NON_CONFORMITY_RESPONSE_NOT_IN_INSPECTION",
                    "The response does not belong to the inspection",
                    List.of(new Violation("responseId", "The response is not part of this inspection")));
        }
    }

    private UUID currentUserId() {
        return authenticatedUserResolver.requireUserId(SecurityContextHolder.getContext().getAuthentication());
    }

    private String auditState(NonConformity value) {
        return "{\"inspectionId\":\"" + value.getInspectionId()
                + "\",\"inspectionItemId\":" + jsonUuid(value.getInspectionItemId())
                + ",\"responseId\":" + jsonUuid(value.getResponseId())
                + ",\"title\":\"" + value.getTitle().replace("\"", "\\\"")
                + "\",\"severity\":\"" + value.getSeverity()
                + "\",\"status\":\"" + value.getStatus() + "\"}";
    }

    private String jsonUuid(UUID value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
