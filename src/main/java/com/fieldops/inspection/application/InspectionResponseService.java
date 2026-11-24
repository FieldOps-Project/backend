package com.fieldops.inspection.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.inspection.domain.model.InspectionResponseValue;
import com.fieldops.inspection.domain.model.InspectionReviewDecision;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionReviewRepository;
import com.fieldops.inspection.presentation.dto.InspectionResponseBatchItemResponse;
import com.fieldops.inspection.presentation.dto.InspectionResponseBatchItemResponse.BatchItemStatus;
import com.fieldops.inspection.presentation.dto.InspectionResponseBatchRequest;
import com.fieldops.inspection.presentation.dto.InspectionResponseBatchResponse;
import com.fieldops.inspection.presentation.dto.InspectionResponseResponse;
import com.fieldops.inspection.presentation.dto.SaveInspectionResponseRequest;
import com.fieldops.shared.domain.exception.BusinessException;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.domain.exception.Violation;
import com.fieldops.shared.presentation.dto.ApiError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
@ConditionalOnBean(InspectionResponseRepository.class)
public class InspectionResponseService implements InspectionResponseReader {

    private static final String RESPONSE_ENTITY = "INSPECTION_RESPONSE";

    private final InspectionRepository inspectionRepository;
    private final InspectionResponseRepository responseRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final InspectionResponseValidator validator;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<InspectionEvidenceRepository> evidenceRepositoryProvider;
    private final ObjectProvider<InspectionReviewRepository> reviewRepositoryProvider;
    private final AuditEventService auditEventService;

    @Autowired
    public InspectionResponseService(
            InspectionRepository inspectionRepository,
            InspectionResponseRepository responseRepository,
            AuthenticatedUserResolver authenticatedUserResolver,
            InspectionResponseValidator validator,
            ObjectMapper objectMapper,
            ObjectProvider<InspectionEvidenceRepository> evidenceRepositoryProvider,
            ObjectProvider<InspectionReviewRepository> reviewRepositoryProvider,
            AuditEventService auditEventService
    ) {
        this.inspectionRepository = inspectionRepository;
        this.responseRepository = responseRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.evidenceRepositoryProvider = evidenceRepositoryProvider;
        this.reviewRepositoryProvider = reviewRepositoryProvider;
        this.auditEventService = auditEventService;
        this.clock = Clock.systemUTC();
    }

    public InspectionResponseService(
            InspectionRepository inspectionRepository,
            InspectionResponseRepository responseRepository,
            AuthenticatedUserResolver authenticatedUserResolver,
            InspectionResponseValidator validator,
            ObjectMapper objectMapper,
            AuditEventService auditEventService
    ) {
        this.inspectionRepository = inspectionRepository;
        this.responseRepository = responseRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.evidenceRepositoryProvider = null;
        this.reviewRepositoryProvider = null;
        this.auditEventService = auditEventService;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public List<InspectionResponseResponse> findAll(UUID inspectionId) {
        loadInspection(inspectionId);
        return responseRepository.findAllByInspectionIdOrderByAnsweredAtServerAsc(inspectionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionResponseResponse upsert(
            UUID inspectionId,
            UUID responseId,
            SaveInspectionResponseRequest request
    ) {
        Inspection inspection = loadInspection(inspectionId);
        assertEditable(inspection);
        return toResponse(upsertForInspection(inspection, correctionScope(inspection), responseId, request));
    }

    /**
     * Applies a response from the synchronization pipeline while keeping the
     * same snapshot, ownership, editability and optimistic-version rules used
     * by the online endpoint.
     */
    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionResponse applyFromSynchronization(
            UUID inspectionId,
            SaveInspectionResponseRequest request
    ) {
        Inspection inspection = loadInspection(inspectionId);
        assertEditable(inspection);
        return upsertForInspection(inspection, correctionScope(inspection), request.id(), request);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionResponseBatchResponse batch(UUID inspectionId, InspectionResponseBatchRequest request) {
        Inspection inspection = loadInspection(inspectionId);
        assertEditable(inspection);
        Set<UUID> correctionScope = correctionScope(inspection);
        List<InspectionResponseBatchItemResponse> results = request.responses().stream()
                .map(item -> applyBatchItem(inspection, correctionScope, item))
                .toList();
        return new InspectionResponseBatchResponse(results);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionExecutionAnswer> findByInspectionId(UUID inspectionId) {
        return responseRepository.findAllByInspectionIdOrderByAnsweredAtServerAsc(inspectionId).stream()
                .map(response -> new InspectionExecutionAnswer(
                        response.getInspectionItemId(),
                        true,
                        response.getConformity() == null ? null : response.getConformity().name(),
                        response.getObservation(),
                        hasEvidence(response.getId())
                ))
                .toList();
    }

    private boolean hasEvidence(UUID responseId) {
        if (evidenceRepositoryProvider == null) {
            return false;
        }
        InspectionEvidenceRepository repository = evidenceRepositoryProvider.getIfAvailable();
        return repository != null && repository.existsByResponseIdAndDeletedAtIsNull(responseId);
    }

    private InspectionResponseBatchItemResponse applyBatchItem(
            Inspection inspection,
            Set<UUID> correctionScope,
            SaveInspectionResponseRequest request
    ) {
        try {
            InspectionResponse response = upsertForInspection(inspection, correctionScope, request.id(), request);
            return new InspectionResponseBatchItemResponse(
                    request.id(), BatchItemStatus.APPLIED, toResponse(response), null, null, List.of()
            );
        } catch (BusinessException exception) {
            List<ApiError.FieldError> fieldErrors = exception instanceof BusinessRuleException businessRule
                    ? businessRule.getViolations().stream()
                    .map(violation -> new ApiError.FieldError(violation.field(), violation.message()))
                    .toList()
                    : List.of();
            return new InspectionResponseBatchItemResponse(
                    request.id(), BatchItemStatus.REJECTED, null, exception.getCode(), exception.getMessage(), fieldErrors
            );
        } catch (InvalidRequestException exception) {
            return new InspectionResponseBatchItemResponse(
                    request.id(), BatchItemStatus.REJECTED, null, exception.getCode(), exception.getMessage(), List.of()
            );
        }
    }

    private InspectionResponse upsertForInspection(
            Inspection inspection,
            Set<UUID> correctionScope,
            UUID responseId,
            SaveInspectionResponseRequest request
    ) {
        if (!responseId.equals(request.id())) {
            throw new InvalidRequestException("RESPONSE_ID_MISMATCH", "The response id must match the URL id");
        }
        if (!correctionScope.isEmpty() && !correctionScope.contains(request.inspectionItemId())) {
            throw new BusinessRuleException(
                    "INSPECTION_ITEM_NOT_IN_CORRECTION",
                    "Only the items listed in the last rejection can be changed",
                    List.of(new Violation("inspectionItemId", "The item is not listed in itemsToCorrect"))
            );
        }
        InspectionItemSnapshot snapshot = inspection.getItemSnapshots().stream()
                .filter(item -> item.getId().equals(request.inspectionItemId()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "INSPECTION_ITEM_NOT_IN_SNAPSHOT",
                        "The response item does not belong to the inspection snapshot",
                        List.of(new Violation("inspectionItemId", "The item is not part of this inspection"))
                ));
        InspectionResponseValue value = validator.validate(snapshot, request);
        UUID answeredBy = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );
        Instant answeredAtServer = Instant.now(clock);
        InspectionResponse existing = responseRepository.findByIdAndInspectionId(responseId, inspection.getId())
                .orElse(null);
        if (existing != null) {
            if (!existing.getInspectionItemId().equals(request.inspectionItemId())) {
                throw new ConflictException(
                        "INSPECTION_RESPONSE_ID_REUSED",
                        "The response id is already linked to another inspection item"
                );
            }
            if (request.baseVersion() != existing.getVersion()) {
                throw new ConflictException(
                        "INSPECTION_RESPONSE_VERSION_CONFLICT",
                        "The response version is stale; reload the inspection before retrying"
                );
            }
            String previousValue = auditValue(existing);
            existing.update(answeredBy, value, request.observation(), request.answeredAtDevice(), answeredAtServer,
                    request.baseVersion());
            InspectionResponse saved = responseRepository.saveAndFlush(existing);
            auditEventService.recordInspectionEvent("RESPONSE_UPDATED", RESPONSE_ENTITY, saved.getId(),
                    saved.getInspectionId(), saved.getAnsweredAtDevice(), previousValue, auditValue(saved));
            return saved;
        }
        if (responseRepository.existsById(responseId)) {
            throw new ConflictException(
                    "INSPECTION_RESPONSE_ID_REUSED",
                    "The response id is already linked to another inspection"
            );
        }
        if (responseRepository.findByInspectionIdAndInspectionItemId(inspection.getId(), request.inspectionItemId())
                .isPresent()) {
            throw new ConflictException(
                    "INSPECTION_RESPONSE_ALREADY_EXISTS",
                    "The inspection already has a response for this item"
            );
        }
        InspectionResponse created = responseRepository.saveAndFlush(InspectionResponse.create(
                responseId,
                inspection.getId(),
                request.inspectionItemId(),
                answeredBy,
                value,
                request.observation(),
                request.answeredAtDevice(),
                answeredAtServer,
                request.baseVersion()
        ));
        auditEventService.recordInspectionEvent("RESPONSE_CREATED", RESPONSE_ENTITY, created.getId(),
                created.getInspectionId(), created.getAnsweredAtDevice(), null, auditValue(created));
        return created;
    }

    private Inspection loadInspection(UUID inspectionId) {
        UUID technicianId = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );
        return inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
    }

    private void assertEditable(Inspection inspection) {
        if (inspection.getStatus() == InspectionStatus.APPROVED) {
            throw new BusinessRuleException(
                    "INSPECTION_APPROVED_READ_ONLY",
                    "Approved inspection data cannot be changed"
            );
        }
        if (inspection.getStatus() == InspectionStatus.REJECTED) {
            throw new BusinessRuleException(
                    "INSPECTION_REOPEN_REQUIRED",
                    "Reopen the rejected inspection before correcting its responses"
            );
        }
        if (inspection.getStatus() == InspectionStatus.SUBMITTED
                || inspection.getStatus() == InspectionStatus.UNDER_REVIEW
                || inspection.getStatus() == InspectionStatus.CANCELED) {
            throw new BusinessRuleException(
                    "INSPECTION_RESPONSES_LOCKED",
                    "Responses cannot be changed after the inspection is submitted"
            );
        }
    }

    /**
     * Items the technician may change while correcting a rejection; empty means the
     * whole inspection is editable. An inspection never submitted cannot have a review,
     * so the lookup is skipped for the first execution.
     */
    private Set<UUID> correctionScope(Inspection inspection) {
        if (inspection.getSubmittedAtServer() == null || reviewRepositoryProvider == null) {
            return Set.of();
        }
        return reviewRepositoryProvider.getObject().findFirstByInspectionIdOrderByReviewCycleDesc(inspection.getId())
                .filter(review -> review.getDecision() == InspectionReviewDecision.REJECTED)
                .map(review -> Set.copyOf(review.getItemsToCorrect()))
                .orElse(Set.of());
    }

    /** Allow-listed response fields kept in the audit trail so a corrected value stays recoverable (RN-083). */
    private String auditValue(InspectionResponse response) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("inspectionItemId", response.getInspectionItemId().toString());
            node.put("valueText", response.getValueText());
            node.put("valueNumber", response.getValueNumber());
            node.put("valueBoolean", response.getValueBoolean());
            node.put("valueDate", response.getValueDate() == null ? null : response.getValueDate().toString());
            node.set("valueJson", response.getValueJson() == null ? null : objectMapper.readTree(response.getValueJson()));
            node.put("conformity", response.getConformity() == null ? null : response.getConformity().name());
            node.put("observation", response.getObservation());
            node.put("answeredAtDevice", response.getAnsweredAtDevice().toString());
            node.put("version", response.getVersion());
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored inspection response JSON is invalid", exception);
        }
    }

    private InspectionResponseResponse toResponse(InspectionResponse response) {
        try {
            return new InspectionResponseResponse(
                    response.getId(), response.getInspectionItemId(), response.getValueText(), response.getValueNumber(),
                    response.getValueBoolean(), response.getValueDate(),
                    response.getValueJson() == null ? null : objectMapper.readTree(response.getValueJson()),
                    response.getConformity(), response.getObservation(), response.getAnsweredAtDevice(),
                    response.getAnsweredAtServer(), response.getAnsweredBy(), response.getBaseVersion(), response.getVersion()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Stored inspection response JSON is invalid", exception);
        }
    }
}
