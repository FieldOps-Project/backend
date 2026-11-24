package com.fieldops.sync.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.inspection.application.InspectionEvidenceService;
import com.fieldops.inspection.application.InspectionResponseService;
import com.fieldops.inspection.application.NonConformityService;
import com.fieldops.inspection.presentation.dto.CreateNonConformityRequest;
import com.fieldops.inspection.presentation.dto.EvidenceMetadataRequest;
import com.fieldops.inspection.presentation.dto.SaveInspectionResponseRequest;
import com.fieldops.inspection.presentation.dto.UpdateNonConformityRequest;
import com.fieldops.shared.domain.exception.BusinessException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.sync.domain.model.SyncOperationResultStatus;
import com.fieldops.sync.domain.model.SyncProcessedOperation;
import com.fieldops.sync.infrastructure.persistence.SyncProcessedOperationRepository;
import com.fieldops.sync.presentation.dto.SyncOperationRequest;
import com.fieldops.sync.presentation.dto.SyncOperationResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class SyncOperationProcessor {

    private final SyncProcessedOperationRepository processedOperationRepository;
    private final InspectionResponseService responseService;
    private final InspectionEvidenceService evidenceService;
    private final NonConformityService nonConformityService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SyncOperationProcessor(
            SyncProcessedOperationRepository processedOperationRepository,
            InspectionResponseService responseService,
            InspectionEvidenceService evidenceService,
            NonConformityService nonConformityService,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.processedOperationRepository = processedOperationRepository;
        this.responseService = responseService;
        this.evidenceService = evidenceService;
        this.nonConformityService = nonConformityService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = Clock.systemUTC();
    }

    /**
     * The advisory lock serializes concurrent deliveries of the same operation
     * id before the idempotency row exists, which a row lock cannot do.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncOperationResult process(UUID deviceId, UUID userId, SyncOperationRequest operation) {
        lock(operation.operationId());
        SyncProcessedOperation existing = processedOperationRepository.findById(operation.operationId()).orElse(null);
        if (existing != null) {
            return alreadyApplied(existing);
        }

        try {
            AppliedEntity applied = apply(operation);
            return persist(
                    deviceId, userId, operation, SyncOperationResultStatus.APPLIED,
                    applied.version(), null, null
            );
        } catch (ConflictException exception) {
            return persist(
                    deviceId, userId, operation, SyncOperationResultStatus.CONFLICT,
                    null, exception.getCode(), exception.getMessage()
            );
        } catch (BusinessException | InvalidRequestException | IllegalArgumentException exception) {
            return persist(
                    deviceId, userId, operation, SyncOperationResultStatus.REJECTED,
                    null, errorCode(exception), exception.getMessage()
            );
        } catch (RuntimeException exception) {
            return persist(
                    deviceId, userId, operation, SyncOperationResultStatus.REJECTED,
                    null, "SYNC_OPERATION_FAILED", "The synchronization operation could not be applied"
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncOperationResult recordSkipped(
            UUID deviceId,
            UUID userId,
            SyncOperationRequest operation,
            SyncOperationResultStatus status,
            String code,
            String message
    ) {
        lock(operation.operationId());
        SyncProcessedOperation existing = processedOperationRepository.findById(operation.operationId()).orElse(null);
        if (existing != null) {
            return alreadyApplied(existing);
        }
        return persist(deviceId, userId, operation, status, null, code, message);
    }

    private AppliedEntity apply(SyncOperationRequest operation) {
        if ("INSPECTION_RESPONSE".equals(operation.entityType())) {
            return applyResponse(operation);
        }
        if ("INSPECTION_EVIDENCE".equals(operation.entityType())) {
            return applyEvidence(operation);
        }
        if ("NON_CONFORMITY".equals(operation.entityType())) {
            return applyNonConformity(operation);
        }
        throw new InvalidRequestException("SYNC_ENTITY_TYPE_UNSUPPORTED", "The synchronization entity type is not supported");
    }

    private AppliedEntity applyResponse(SyncOperationRequest operation) {
        requireOperationType(operation, "UPSERT");
        SyncPayloads.Response payload = readPayload(operation, SyncPayloads.Response.class);
        UUID inspectionId = requireInspectionId(payload.inspectionId());
        SaveInspectionResponseRequest request = new SaveInspectionResponseRequest(
                operation.entityId(),
                payload.inspectionItemId(),
                payload.valueText(),
                payload.valueNumber(),
                payload.valueBoolean(),
                payload.valueDate(),
                payload.valueJson(),
                payload.conformity(),
                payload.observation(),
                payload.answeredAtDevice() == null ? clock.instant() : payload.answeredAtDevice(),
                operation.baseVersion()
        );
        return new AppliedEntity(
                responseService.applyFromSynchronization(inspectionId, request).getVersion()
        );
    }

    private AppliedEntity applyEvidence(SyncOperationRequest operation) {
        requireOperationType(operation, "UPLOAD");
        SyncPayloads.Evidence payload = readPayload(operation, SyncPayloads.Evidence.class);
        UUID inspectionId = requireInspectionId(payload.inspectionId());
        if (payload.contentBase64() == null || payload.contentBase64().isBlank()) {
            throw new InvalidRequestException("EVIDENCE_CONTENT_REQUIRED", "Evidence content is required for synchronization");
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(payload.contentBase64());
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("EVIDENCE_CONTENT_INVALID", "Evidence content is not valid base64");
        }
        EvidenceMetadataRequest metadata = new EvidenceMetadataRequest(
                operation.entityId(), payload.responseId(), payload.nonConformityId(), payload.type(),
                payload.capturedAtDevice() == null ? clock.instant() : payload.capturedAtDevice(),
                payload.description(), payload.latitude(), payload.longitude(), payload.accuracyMeters()
        );
        return new AppliedEntity(
                evidenceService.uploadFromSynchronization(
                        inspectionId, metadata, content, payload.mimeType()
                ).getVersion()
        );
    }

    private AppliedEntity applyNonConformity(SyncOperationRequest operation) {
        SyncPayloads.NonConformity payload = readPayload(operation, SyncPayloads.NonConformity.class);
        UUID inspectionId = requireInspectionId(payload.inspectionId());
        if ("CREATE".equals(operation.operationType()) || "UPSERT".equals(operation.operationType())) {
            CreateNonConformityRequest request = new CreateNonConformityRequest(
                    operation.entityId(), payload.inspectionItemId(), payload.responseId(), payload.title(),
                    payload.description(), payload.severity(),
                    payload.createdAtDevice() == null ? clock.instant() : payload.createdAtDevice()
            );
            return new AppliedEntity(
                    nonConformityService.create(inspectionId, request).getVersion()
            );
        }
        if ("UPDATE".equals(operation.operationType())) {
            UpdateNonConformityRequest request = new UpdateNonConformityRequest(
                    payload.title(), payload.description(), payload.severity()
            );
            return new AppliedEntity(nonConformityService.update(operation.entityId(), request).getVersion());
        }
        throw new InvalidRequestException("SYNC_OPERATION_TYPE_UNSUPPORTED", "The non-conformity operation type is not supported");
    }

    private void requireOperationType(SyncOperationRequest operation, String expected) {
        if (!expected.equals(operation.operationType())) {
            throw new InvalidRequestException(
                    "SYNC_OPERATION_TYPE_UNSUPPORTED",
                    "The operation type is not supported for " + operation.entityType()
            );
        }
    }

    private <T> T readPayload(SyncOperationRequest operation, Class<T> type) {
        if (operation.payload() == null || operation.payload().isNull()) {
            throw new InvalidRequestException("SYNC_PAYLOAD_INVALID", "The synchronization payload is required");
        }
        try {
            return objectMapper.treeToValue(operation.payload(), type);
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException("SYNC_PAYLOAD_INVALID", "The synchronization payload is invalid");
        }
    }

    private UUID requireInspectionId(UUID inspectionId) {
        if (inspectionId == null) {
            throw new InvalidRequestException(
                    "SYNC_INSPECTION_ID_REQUIRED",
                    "The synchronization payload must identify the inspection"
            );
        }
        return inspectionId;
    }

    private SyncOperationResult persist(
            UUID deviceId,
            UUID userId,
            SyncOperationRequest operation,
            SyncOperationResultStatus status,
            Integer entityVersion,
            String code,
            String message
    ) {
        SyncProcessedOperation saved = processedOperationRepository.saveAndFlush(SyncProcessedOperation.record(
                operation.operationId(), deviceId, userId, operation.entityType(), operation.entityId(), status,
                entityVersion, truncate(code, 100), truncate(message, 500), clock.instant()
        ));
        return new SyncOperationResult(
                saved.getOperationId(), saved.getResultStatus(), saved.getEntityVersion(),
                saved.getResultCode(), saved.getResultMessage()
        );
    }

    private SyncOperationResult alreadyApplied(SyncProcessedOperation existing) {
        return new SyncOperationResult(
                existing.getOperationId(), SyncOperationResultStatus.ALREADY_APPLIED,
                existing.getEntityVersion(), existing.getResultCode(), existing.getResultMessage()
        );
    }

    private void lock(UUID operationId) {
        long key1 = operationId.getMostSignificantBits();
        long key2 = operationId.getLeastSignificantBits();
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?, ?)",
                statement -> {
                    statement.setLong(1, key1);
                    statement.setLong(2, key2);
                },
                resultSet -> null
        );
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getCode();
        }
        if (exception instanceof InvalidRequestException invalidRequestException) {
            return invalidRequestException.getCode();
        }
        return "SYNC_OPERATION_REJECTED";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record AppliedEntity(int version) {
    }
}
