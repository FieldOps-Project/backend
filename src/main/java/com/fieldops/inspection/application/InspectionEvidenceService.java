package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.domain.model.FieldPosition;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionEvidence;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.inspection.infrastructure.storage.EvidenceStorage;
import com.fieldops.inspection.presentation.dto.EvidenceMetadataRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.PayloadTooLargeException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.domain.exception.Violation;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InspectionEvidenceService {

    private static final String EVIDENCE_ENTITY = "INSPECTION_EVIDENCE";

    private final InspectionRepository inspectionRepository;
    private final InspectionEvidenceRepository evidenceRepository;
    private final InspectionResponseRepository responseRepository;
    private final NonConformityRepository nonConformityRepository;
    private final EvidenceStorage evidenceStorage;
    private final EvidenceFileValidator fileValidator;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final AuditEventService auditEventService;
    private final Clock clock;
    private final long maxSizeBytes;

    public InspectionEvidenceService(
            InspectionRepository inspectionRepository,
            InspectionEvidenceRepository evidenceRepository,
            InspectionResponseRepository responseRepository,
            NonConformityRepository nonConformityRepository,
            EvidenceStorage evidenceStorage,
            EvidenceFileValidator fileValidator,
            AuthenticatedUserResolver authenticatedUserResolver,
            AuditEventService auditEventService,
            @Value("${app.evidence.max-size-bytes:10485760}") long maxSizeBytes
    ) {
        this.inspectionRepository = inspectionRepository;
        this.evidenceRepository = evidenceRepository;
        this.responseRepository = responseRepository;
        this.nonConformityRepository = nonConformityRepository;
        this.evidenceStorage = evidenceStorage;
        this.fileValidator = fileValidator;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.auditEventService = auditEventService;
        this.clock = Clock.systemUTC();
        this.maxSizeBytes = maxSizeBytes;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionEvidence upload(UUID inspectionId, EvidenceMetadataRequest metadata, MultipartFile file) {
        assertEditable(loadForTechnician(inspectionId));
        InspectionEvidence existing = evidenceRepository.findById(metadata.id()).orElse(null);
        if (existing != null) {
            if (!existing.getInspectionId().equals(inspectionId)) {
                throw new ConflictException("EVIDENCE_ID_REUSED", "The evidence id is already linked to another inspection");
            }
            return existing;
        }

        validateResponseLink(inspectionId, metadata.responseId());
        validateNonConformityLink(inspectionId, metadata.nonConformityId());
        validateCoordinates(metadata);
        EvidenceFileValidator.ValidatedEvidenceFile validated = fileValidator.validate(file, maxSizeBytes);
        UUID actor = currentUserId();
        Instant now = Instant.now(clock);
        String storageKey = "inspections/" + inspectionId + "/" + metadata.id() + "." + validated.extension();
        evidenceStorage.store(storageKey, validated.content());
        InspectionEvidence evidence = InspectionEvidence.create(
                metadata.id(), inspectionId, metadata.responseId(), metadata.nonConformityId(), metadata.type(), storageKey,
                validated.mimeType(), validated.content().length, checksum(validated.content()), metadata.description(),
                metadata.latitude(), metadata.longitude(), metadata.accuracyMeters(), metadata.capturedAtDevice(),
                now, now, actor
        );
        InspectionEvidence saved;
        try {
            saved = evidenceRepository.saveAndFlush(evidence);
        } catch (RuntimeException exception) {
            evidenceStorage.delete(storageKey);
            throw exception;
        }
        auditEventService.recordInspectionEvent("EVIDENCE_ADDED", EVIDENCE_ENTITY, saved.getId(), saved.getInspectionId(),
                saved.getCapturedAtDevice(), null, auditState(saved));
        return saved;
    }

    @Transactional(noRollbackFor = ConflictException.class)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionEvidence uploadFromSynchronization(
            UUID inspectionId,
            EvidenceMetadataRequest metadata,
            byte[] content,
            String contentType
    ) {
        assertEditable(loadForTechnician(inspectionId));
        InspectionEvidence existing = evidenceRepository.findById(metadata.id()).orElse(null);
        if (existing != null) {
            if (!existing.getInspectionId().equals(inspectionId)) {
                throw new ConflictException("EVIDENCE_ID_REUSED", "The evidence id is already linked to another inspection");
            }
            if (existing.isDeleted()) {
                throw new ConflictException("EVIDENCE_DELETED_CONFLICT", "The evidence was deleted on the server");
            }
            return receiveFile(existing, content);
        }

        validateResponseLink(inspectionId, metadata.responseId());
        validateNonConformityLink(inspectionId, metadata.nonConformityId());
        validateCoordinates(metadata);
        EvidenceFileValidator.ValidatedEvidenceFile validated = fileValidator.validate(content, contentType, maxSizeBytes);
        UUID actor = currentUserId();
        Instant now = Instant.now(clock);
        String storageKey = "inspections/" + inspectionId + "/" + metadata.id() + "." + validated.extension();
        evidenceStorage.store(storageKey, validated.content());
        InspectionEvidence evidence = InspectionEvidence.create(
                metadata.id(), inspectionId, metadata.responseId(), metadata.nonConformityId(), metadata.type(), storageKey,
                validated.mimeType(), validated.content().length, checksum(validated.content()), metadata.description(),
                metadata.latitude(), metadata.longitude(), metadata.accuracyMeters(), metadata.capturedAtDevice(),
                now, now, actor
        );
        try {
            InspectionEvidence saved = evidenceRepository.saveAndFlush(evidence);
            auditEventService.recordInspectionEvent("EVIDENCE_ADDED", EVIDENCE_ENTITY, saved.getId(), saved.getInspectionId(),
                saved.getCapturedAtDevice(), null, auditState(saved));
            return saved;
        } catch (RuntimeException exception) {
            evidenceStorage.delete(storageKey);
            throw exception;
        }
    }

    /**
     * Applies the metadata of an evidence whose binary is synchronized by a later UPLOAD
     * operation (RN-078): the evidence stays with {@code uploaded_at} null until then.
     */
    @Transactional(noRollbackFor = ConflictException.class)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionEvidence registerFromSynchronization(
            UUID inspectionId,
            EvidenceMetadataRequest metadata,
            String mimeType,
            long sizeBytes,
            String checksum
    ) {
        return registerFromSynchronization(inspectionId, metadata, mimeType, sizeBytes, checksum, null);
    }

    /**
     * Applies evidence metadata from synchronization, checking {@code baseVersion} and deletion
     * state if the evidence already exists on the server.
     */
    @Transactional(noRollbackFor = ConflictException.class)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionEvidence registerFromSynchronization(
            UUID inspectionId,
            EvidenceMetadataRequest metadata,
            String mimeType,
            long sizeBytes,
            String checksum,
            Integer baseVersion
    ) {
        assertEditable(loadForTechnician(inspectionId));
        InspectionEvidence existing = evidenceRepository.findById(metadata.id()).orElse(null);
        if (existing != null) {
            if (!existing.getInspectionId().equals(inspectionId)) {
                throw new ConflictException("EVIDENCE_ID_REUSED", "The evidence id is already linked to another inspection");
            }
            if (existing.isDeleted()) {
                throw new ConflictException("EVIDENCE_DELETED_CONFLICT", "The evidence was deleted on the server");
            }
            if (baseVersion != null && baseVersion != existing.getVersion()) {
                throw new ConflictException(
                        "EVIDENCE_VERSION_CONFLICT",
                        "The evidence version is stale; reload the inspection before retrying"
                );
            }
            return existing;
        }

        validateResponseLink(inspectionId, metadata.responseId());
        validateNonConformityLink(inspectionId, metadata.nonConformityId());
        validateCoordinates(metadata);
        String extension = fileValidator.requireSupportedType(mimeType);
        if (sizeBytes <= 0 || sizeBytes > maxSizeBytes) {
            throw new PayloadTooLargeException("EVIDENCE_TOO_LARGE", "The evidence exceeds the configured size limit");
        }
        Instant now = Instant.now(clock);
        InspectionEvidence evidence = InspectionEvidence.create(
                metadata.id(), inspectionId, metadata.responseId(), metadata.nonConformityId(), metadata.type(),
                "inspections/" + inspectionId + "/" + metadata.id() + "." + extension,
                mimeType.toLowerCase(), sizeBytes, checksum, metadata.description(),
                metadata.latitude(), metadata.longitude(), metadata.accuracyMeters(), metadata.capturedAtDevice(),
                now, null, currentUserId()
        );
        InspectionEvidence saved = evidenceRepository.saveAndFlush(evidence);
        auditEventService.recordInspectionEvent("EVIDENCE_ADDED", EVIDENCE_ENTITY, saved.getId(), saved.getInspectionId(),
                saved.getCapturedAtDevice(), null, auditState(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EVIDENCE_READ)
    public List<InspectionEvidence> findAll(UUID inspectionId) {
        loadForReadableUser(inspectionId);
        return evidenceRepository.findAllByInspectionIdAndDeletedAtIsNullOrderByServerReceivedAtAsc(inspectionId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EVIDENCE_READ)
    public InspectionEvidenceDownload download(UUID evidenceId) {
        InspectionEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Evidence", evidenceId));
        loadForReadableUser(evidence.getInspectionId());
        if (evidence.isDeleted() || !evidence.isFileReceived()) {
            throw ResourceNotFoundException.of("Evidence", evidenceId);
        }
        return new InspectionEvidenceDownload(evidence, evidenceStorage.read(evidence.getStorageKey()));
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public void delete(UUID evidenceId) {
        InspectionEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Evidence", evidenceId));
        Inspection inspection = loadForTechnician(evidence.getInspectionId());
        if (evidence.isDeleted()) {
            throw ResourceNotFoundException.of("Evidence", evidenceId);
        }
        if (inspection.getStatus() == InspectionStatus.APPROVED) {
            throw new BusinessRuleException("EVIDENCE_READ_ONLY", "Evidence from an approved inspection is read-only");
        }
        String before = auditState(evidence);
        evidence.remove(currentUserId(), Instant.now(clock));
        evidenceStorage.delete(evidence.getStorageKey());
        InspectionEvidence saved = evidenceRepository.saveAndFlush(evidence);
        auditEventService.recordInspectionEvent("EVIDENCE_REMOVED", EVIDENCE_ENTITY, saved.getId(), saved.getInspectionId(),
                null, before, auditState(saved));
    }

    /**
     * Stores the binary of an existing evidence under its own storage key. An identical
     * resend (same checksum) is a no-op, so the object is never rewritten needlessly.
     */
    private InspectionEvidence receiveFile(InspectionEvidence evidence, byte[] content) {
        String checksum = checksum(content);
        if (evidence.isFileReceived() && evidence.getChecksum().equals(checksum)) {
            return evidence;
        }
        EvidenceFileValidator.ValidatedEvidenceFile validated =
                fileValidator.validate(content, evidence.getMimeType(), maxSizeBytes);
        evidenceStorage.replace(evidence.getStorageKey(), validated.content());
        evidence.receiveFile(validated.content().length, checksum, Instant.now(clock));
        return evidenceRepository.saveAndFlush(evidence);
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

    private Inspection loadForReadableUser(UUID inspectionId) {
        User user = authenticatedUserResolver.requireUser(SecurityContextHolder.getContext().getAuthentication());
        if (user.getRole() == UserRole.TECHNICIAN) {
            return inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, user.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
        }
        if (user.getRole() == UserRole.SUPERVISOR) {
            return inspectionRepository.findByIdAndSupervisorId(inspectionId, user.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
        }
        throw ResourceNotFoundException.of("Inspection", inspectionId);
    }

    private void validateResponseLink(UUID inspectionId, UUID responseId) {
        if (responseId != null && responseRepository.findByIdAndInspectionId(responseId, inspectionId).isEmpty()) {
            throw new BusinessRuleException(
                    "EVIDENCE_RESPONSE_NOT_IN_INSPECTION",
                    "The response does not belong to the inspection",
                    List.of(new Violation("responseId", "The response is not part of this inspection"))
            );
        }
    }

    private void validateNonConformityLink(UUID inspectionId, UUID nonConformityId) {
        if (nonConformityId != null && nonConformityRepository.findByIdAndInspectionId(nonConformityId, inspectionId).isEmpty()) {
            throw new BusinessRuleException(
                    "EVIDENCE_NON_CONFORMITY_NOT_IN_INSPECTION",
                    "The non-conformity does not belong to the inspection",
                    List.of(new Violation("nonConformityId", "The non-conformity is not part of this inspection"))
            );
        }
    }

    private void validateCoordinates(EvidenceMetadataRequest metadata) {
        boolean hasLatitude = metadata.latitude() != null;
        boolean hasLongitude = metadata.longitude() != null;
        if (hasLatitude != hasLongitude) {
            throw new BusinessRuleException(
                    "EVIDENCE_LOCATION_INVALID",
                    "Latitude and longitude must be sent together",
                    List.of(new Violation("location", "Both latitude and longitude are required"))
            );
        }
        if (hasLatitude) {
            try {
                FieldPosition.of(metadata.latitude(), metadata.longitude(), metadata.accuracyMeters(),
                        metadata.capturedAtDevice());
            } catch (IllegalArgumentException exception) {
                throw new BusinessRuleException(
                        "EVIDENCE_LOCATION_INVALID", "The evidence location is invalid",
                        List.of(new Violation("location", exception.getMessage()))
                );
            }
        }
    }

    private UUID currentUserId() {
        return authenticatedUserResolver.requireUserId(SecurityContextHolder.getContext().getAuthentication());
    }

    private String checksum(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String auditState(InspectionEvidence evidence) {
        return "{\"inspectionId\":\"" + evidence.getInspectionId()
                + "\",\"responseId\":" + jsonUuid(evidence.getResponseId())
                + ",\"mimeType\":\"" + evidence.getMimeType()
                + "\",\"sizeBytes\":" + evidence.getSizeBytes()
                + ",\"checksum\":\"" + evidence.getChecksum()
                + "\",\"deletedAt\":" + (evidence.getDeletedAt() == null ? "null" : "\"" + evidence.getDeletedAt() + "\"")
                + "}";
    }

    private String jsonUuid(UUID value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
