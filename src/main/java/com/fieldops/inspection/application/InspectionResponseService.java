package com.fieldops.inspection.application;

import tools.jackson.databind.ObjectMapper;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.inspection.domain.model.InspectionResponseValue;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InspectionResponseService implements InspectionResponseReader {

    private final InspectionRepository inspectionRepository;
    private final InspectionResponseRepository responseRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final InspectionResponseValidator validator;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<InspectionEvidenceRepository> evidenceRepositoryProvider;

    @Autowired
    public InspectionResponseService(
            InspectionRepository inspectionRepository,
            InspectionResponseRepository responseRepository,
            AuthenticatedUserResolver authenticatedUserResolver,
            InspectionResponseValidator validator,
            ObjectMapper objectMapper,
            ObjectProvider<InspectionEvidenceRepository> evidenceRepositoryProvider
    ) {
        this.inspectionRepository = inspectionRepository;
        this.responseRepository = responseRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.evidenceRepositoryProvider = evidenceRepositoryProvider;
        this.clock = Clock.systemUTC();
    }

    public InspectionResponseService(
            InspectionRepository inspectionRepository,
            InspectionResponseRepository responseRepository,
            AuthenticatedUserResolver authenticatedUserResolver,
            InspectionResponseValidator validator,
            ObjectMapper objectMapper
    ) {
        this.inspectionRepository = inspectionRepository;
        this.responseRepository = responseRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.evidenceRepositoryProvider = null;
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
        return toResponse(upsertForInspection(inspection, responseId, request));
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
        return upsertForInspection(inspection, request.id(), request);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionResponseBatchResponse batch(UUID inspectionId, InspectionResponseBatchRequest request) {
        Inspection inspection = loadInspection(inspectionId);
        assertEditable(inspection);
        List<InspectionResponseBatchItemResponse> results = request.responses().stream()
                .map(item -> applyBatchItem(inspection, item))
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
            SaveInspectionResponseRequest request
    ) {
        try {
            InspectionResponse response = upsertForInspection(inspection, request.id(), request);
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
            UUID responseId,
            SaveInspectionResponseRequest request
    ) {
        if (!responseId.equals(request.id())) {
            throw new InvalidRequestException("RESPONSE_ID_MISMATCH", "The response id must match the URL id");
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
            existing.update(answeredBy, value, request.observation(), request.answeredAtDevice(), answeredAtServer,
                    request.baseVersion());
            return responseRepository.saveAndFlush(existing);
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
        return responseRepository.saveAndFlush(InspectionResponse.create(
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
        if (inspection.getStatus() == InspectionStatus.SUBMITTED
                || inspection.getStatus() == InspectionStatus.UNDER_REVIEW
                || inspection.getStatus() == InspectionStatus.CANCELED) {
            throw new BusinessRuleException(
                    "INSPECTION_RESPONSES_LOCKED",
                    "Responses cannot be changed after the inspection is submitted"
            );
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
