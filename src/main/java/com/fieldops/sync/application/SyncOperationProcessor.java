package com.fieldops.sync.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fieldops.audit.application.AuditEventService;
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
import com.fieldops.shared.infrastructure.filter.RequestIdFilter;
import com.fieldops.sync.domain.model.SyncOperationResultStatus;
import com.fieldops.sync.domain.model.SyncProcessedOperation;
import com.fieldops.sync.infrastructure.persistence.SyncProcessedOperationRepository;
import com.fieldops.sync.presentation.dto.SyncOperationRequest;
import com.fieldops.sync.presentation.dto.SyncOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SyncOperationProcessor {

    private static final Logger log = LoggerFactory.getLogger(SyncOperationProcessor.class);
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final SyncProcessedOperationRepository processedOperationRepository;
    private final InspectionResponseService responseService;
    private final InspectionEvidenceService evidenceService;
    private final NonConformityService nonConformityService;
    private final SyncConflictResolver conflictResolver;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public SyncOperationProcessor(
            SyncProcessedOperationRepository processedOperationRepository,
            InspectionResponseService responseService,
            InspectionEvidenceService evidenceService,
            NonConformityService nonConformityService,
            SyncConflictResolver conflictResolver,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.processedOperationRepository = processedOperationRepository;
        this.responseService = responseService;
        this.evidenceService = evidenceService;
        this.nonConformityService = nonConformityService;
        this.conflictResolver = conflictResolver;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = Clock.systemUTC();
    }

    public SyncOperationProcessor(
            SyncProcessedOperationRepository processedOperationRepository,
            InspectionResponseService responseService,
            InspectionEvidenceService evidenceService,
            NonConformityService nonConformityService,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this(processedOperationRepository, responseService, evidenceService, nonConformityService,
                null, objectMapper, jdbcTemplate);
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

        UUID inspectionId = extractUuidField(operation.payload(), "inspectionId");
        UUID itemId = extractUuidField(operation.payload(), "inspectionItemId");

        MDC.put(AuditEventService.DEVICE_ID_MDC_KEY, deviceId.toString());
        try {
            if (conflictResolver != null) {
                Optional<SyncConflictResolver.ConflictDetails> precedenceConflict =
                        conflictResolver.checkInspectionPrecedence(inspectionId, userId);
                if (precedenceConflict.isPresent()) {
                    return recordConflict(deviceId, userId, operation, precedenceConflict.get());
                }
            }

            AppliedEntity applied = apply(operation);
            return persist(
                    deviceId, userId, operation, SyncOperationResultStatus.APPLIED,
                    applied.version(), null, null, null
            );
        } catch (ConflictException exception) {
            SyncConflictResolver.ConflictDetails details = conflictResolver != null
                    ? conflictResolver.resolveConflict(operation, inspectionId, itemId, exception)
                    : new SyncConflictResolver.ConflictDetails(null, exception.getCode(), exception.getMessage(), null);
            return recordConflict(deviceId, userId, operation, details);
        } catch (BusinessException | InvalidRequestException | IllegalArgumentException exception) {
            return persist(
                    deviceId, userId, operation, SyncOperationResultStatus.REJECTED,
                    null, errorCode(exception), exception.getMessage(), null
            );
        } catch (RuntimeException exception) {
            return persist(
                    deviceId, userId, operation, SyncOperationResultStatus.REJECTED,
                    null, "SYNC_OPERATION_FAILED", "The synchronization operation could not be applied", null
            );
        } finally {
            MDC.remove(AuditEventService.DEVICE_ID_MDC_KEY);
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
        return persist(deviceId, userId, operation, status, null, code, message, null);
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

    /**
     * CREATE/UPSERT applies only the metadata; UPLOAD sends the binary and also creates
     * the evidence when its metadata was not synchronized before (RN-078).
     */
    private AppliedEntity applyEvidence(SyncOperationRequest operation) {
        SyncPayloads.Evidence payload = readPayload(operation, SyncPayloads.Evidence.class);
        UUID inspectionId = requireInspectionId(payload.inspectionId());
        if ("CREATE".equals(operation.operationType()) || "UPSERT".equals(operation.operationType())) {
            if (payload.sizeBytes() == null || payload.checksum() == null
                    || !SHA_256_HEX.matcher(payload.checksum()).matches()) {
                throw new InvalidRequestException(
                        "EVIDENCE_FILE_DESCRIPTION_REQUIRED",
                        "Evidence metadata must declare the file size and its SHA-256 checksum"
                );
            }
            return new AppliedEntity(
                    evidenceService.registerFromSynchronization(
                            inspectionId, evidenceMetadata(operation, payload), payload.mimeType(),
                            payload.sizeBytes(), payload.checksum(), operation.baseVersion()
                    ).getVersion()
            );
        }
        requireOperationType(operation, "UPLOAD");
        if (payload.contentBase64() == null || payload.contentBase64().isBlank()) {
            throw new InvalidRequestException("EVIDENCE_CONTENT_REQUIRED", "Evidence content is required for synchronization");
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(payload.contentBase64());
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("EVIDENCE_CONTENT_INVALID", "Evidence content is not valid base64");
        }
        return new AppliedEntity(
                evidenceService.uploadFromSynchronization(
                        inspectionId, evidenceMetadata(operation, payload), content, payload.mimeType()
                ).getVersion()
        );
    }

    private EvidenceMetadataRequest evidenceMetadata(SyncOperationRequest operation, SyncPayloads.Evidence payload) {
        return new EvidenceMetadataRequest(
                operation.entityId(), payload.responseId(), payload.nonConformityId(), payload.type(),
                payload.capturedAtDevice() == null ? clock.instant() : payload.capturedAtDevice(),
                payload.description(), payload.latitude(), payload.longitude(), payload.accuracyMeters()
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
                    nonConformityService.createFromSynchronization(
                            inspectionId, request, operation.baseVersion()
                    ).getVersion()
            );
        }
        if ("UPDATE".equals(operation.operationType())) {
            UpdateNonConformityRequest request = new UpdateNonConformityRequest(
                    payload.title(), payload.description(), payload.severity()
            );
            return new AppliedEntity(
                    nonConformityService.updateFromSynchronization(
                            operation.entityId(), request, operation.baseVersion()
                    ).getVersion()
            );
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
        } catch (JacksonException exception) {
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

    private SyncOperationResult recordConflict(
            UUID deviceId,
            UUID userId,
            SyncOperationRequest operation,
            SyncConflictResolver.ConflictDetails details
    ) {
        log.warn(
                "Sync conflict detected: requestId={}, deviceId={}, operationId={}, entityType={}, entityId={}, baseVersion={}, serverVersion={}, code={}",
                MDC.get(RequestIdFilter.MDC_KEY),
                deviceId,
                operation.operationId(),
                operation.entityType(),
                operation.entityId(),
                operation.baseVersion(),
                details.entityVersion(),
                details.errorCode()
        );
        return persist(
                deviceId, userId, operation, SyncOperationResultStatus.CONFLICT,
                details.entityVersion(), details.errorCode(), details.errorMessage(), details.serverState()
        );
    }

    private SyncOperationResult persist(
            UUID deviceId,
            UUID userId,
            SyncOperationRequest operation,
            SyncOperationResultStatus status,
            Integer entityVersion,
            String code,
            String message,
            JsonNode serverState
    ) {
        String serializedState = serializeServerState(serverState);
        SyncProcessedOperation saved = processedOperationRepository.saveAndFlush(SyncProcessedOperation.record(
                operation.operationId(), deviceId, userId, operation.entityType(), operation.entityId(), status,
                entityVersion, truncate(code, 100), truncate(message, 500), serializedState, clock.instant()
        ));
        return new SyncOperationResult(
                saved.getOperationId(), saved.getResultStatus(), saved.getEntityVersion(),
                saved.getResultCode(), saved.getResultMessage(), serverState
        );
    }

    private SyncOperationResult alreadyApplied(SyncProcessedOperation existing) {
        SyncOperationResultStatus status = existing.getResultStatus() == SyncOperationResultStatus.APPLIED
                ? SyncOperationResultStatus.ALREADY_APPLIED
                : existing.getResultStatus();
        return new SyncOperationResult(
                existing.getOperationId(), status,
                existing.getEntityVersion(), existing.getResultCode(), existing.getResultMessage(),
                deserializeServerState(existing.getServerState())
        );
    }

    private UUID extractUuidField(JsonNode payload, String fieldName) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        JsonNode field = payload.get(fieldName);
        if (field == null || !field.isString()) {
            return null;
        }
        try {
            return UUID.fromString(field.asString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String serializeServerState(JsonNode serverState) {
        if (serverState == null || serverState.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(serverState);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize conflict server state", exception);
        }
    }

    private JsonNode deserializeServerState(String serverState) {
        if (serverState == null || serverState.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(serverState);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private void lock(UUID operationId) {
        long key = operationId.getMostSignificantBits() ^ operationId.getLeastSignificantBits();
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?)",
                statement -> statement.setLong(1, key),
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
