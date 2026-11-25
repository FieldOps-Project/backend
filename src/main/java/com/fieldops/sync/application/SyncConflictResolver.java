package com.fieldops.sync.application;

import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionEvidence;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.NonConformity;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.sync.presentation.dto.SyncOperationRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves synchronization conflicts against the current server state so devices
 * receive the server version, structured state snapshot, and an explicit conflict
 * code without discarding pending local data (RN-075, RN-076, RN-077).
 */
@Component
public class SyncConflictResolver {

    public static final String CODE_VERSION_CONFLICT = "SYNC_VERSION_CONFLICT";
    public static final String CODE_APPROVED_CONFLICT = "SYNC_INSPECTION_APPROVED_CONFLICT";
    public static final String CODE_CANCELED_CONFLICT = "SYNC_INSPECTION_CANCELED_CONFLICT";

    private static final Set<String> VERSION_CONFLICT_CODES = Set.of(
            "INSPECTION_RESPONSE_VERSION_CONFLICT",
            "NON_CONFORMITY_VERSION_CONFLICT",
            "EVIDENCE_VERSION_CONFLICT",
            "INSPECTION_RESPONSE_ALREADY_EXISTS",
            CODE_VERSION_CONFLICT
    );

    private final InspectionRepository inspectionRepository;
    private final InspectionResponseRepository responseRepository;
    private final NonConformityRepository nonConformityRepository;
    private final InspectionEvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;

    public SyncConflictResolver(
            InspectionRepository inspectionRepository,
            InspectionResponseRepository responseRepository,
            NonConformityRepository nonConformityRepository,
            InspectionEvidenceRepository evidenceRepository,
            ObjectMapper objectMapper
    ) {
        this.inspectionRepository = inspectionRepository;
        this.responseRepository = responseRepository;
        this.nonConformityRepository = nonConformityRepository;
        this.evidenceRepository = evidenceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks server-wins precedence for terminal inspections (RN-076 approved, RN-077 canceled).
     */
    public Optional<ConflictDetails> checkInspectionPrecedence(UUID inspectionId, UUID technicianId) {
        if (inspectionId == null || technicianId == null) {
            return Optional.empty();
        }
        return inspectionRepository.findById(inspectionId)
                .filter(inspection -> technicianId.equals(inspection.getTechnicianId()))
                .flatMap(this::terminalInspectionConflict);
    }

    /**
     * Builds full conflict details (version, code, message, and current server snapshot)
     * when applying an operation raises a {@link ConflictException}.
     */
    public ConflictDetails resolveConflict(
            SyncOperationRequest operation,
            UUID inspectionId,
            UUID itemId,
            ConflictException exception
    ) {
        String code = VERSION_CONFLICT_CODES.contains(exception.getCode())
                ? CODE_VERSION_CONFLICT
                : exception.getCode();
        String message = CODE_VERSION_CONFLICT.equals(code)
                ? "The record was modified on the server after the last synchronization"
                : exception.getMessage();

        return switch (operation.entityType()) {
            case "INSPECTION_RESPONSE" -> resolveResponseConflict(operation.entityId(), inspectionId, itemId, code, message);
            case "NON_CONFORMITY" -> resolveNonConformityConflict(operation.entityId(), code, message);
            case "INSPECTION_EVIDENCE" -> resolveEvidenceConflict(operation.entityId(), code, message);
            default -> new ConflictDetails(null, code, message, null);
        };
    }

    private Optional<ConflictDetails> terminalInspectionConflict(Inspection inspection) {
        if (inspection.getStatus() == InspectionStatus.APPROVED) {
            return Optional.of(new ConflictDetails(
                    inspection.getVersion(),
                    CODE_APPROVED_CONFLICT,
                    "Approved inspection data on the server takes precedence and cannot be updated",
                    inspectionSnapshot(inspection)
            ));
        }
        if (inspection.getStatus() == InspectionStatus.CANCELED) {
            return Optional.of(new ConflictDetails(
                    inspection.getVersion(),
                    CODE_CANCELED_CONFLICT,
                    "The inspection was canceled on the server; pending local changes were preserved on the device",
                    inspectionSnapshot(inspection)
            ));
        }
        return Optional.empty();
    }

    private ConflictDetails resolveResponseConflict(
            UUID entityId,
            UUID inspectionId,
            UUID itemId,
            String code,
            String message
    ) {
        Optional<InspectionResponse> existing = responseRepository.findById(entityId);
        if (existing.isEmpty() && inspectionId != null && itemId != null) {
            existing = responseRepository.findByInspectionIdAndInspectionItemId(inspectionId, itemId);
        }
        return existing
                .map(response -> new ConflictDetails(response.getVersion(), code, message, responseSnapshot(response)))
                .orElseGet(() -> new ConflictDetails(null, code, message, null));
    }

    private ConflictDetails resolveNonConformityConflict(UUID entityId, String code, String message) {
        return nonConformityRepository.findById(entityId)
                .map(nc -> new ConflictDetails(nc.getVersion(), code, message, nonConformitySnapshot(nc)))
                .orElseGet(() -> new ConflictDetails(null, code, message, null));
    }

    private ConflictDetails resolveEvidenceConflict(UUID entityId, String code, String message) {
        return evidenceRepository.findById(entityId)
                .map(ev -> new ConflictDetails(ev.getVersion(), code, message, evidenceSnapshot(ev)))
                .orElseGet(() -> new ConflictDetails(null, code, message, null));
    }

    private JsonNode inspectionSnapshot(Inspection inspection) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", inspection.getId().toString());
        node.put("status", inspection.getStatus().name());
        node.put("approvedAt", inspection.getApprovedAt() == null ? null : inspection.getApprovedAt().toString());
        node.put("canceledAt", inspection.getCanceledAt() == null ? null : inspection.getCanceledAt().toString());
        node.put("canceledReason", inspection.getCanceledReason());
        node.put("updatedAt", inspection.getUpdatedAt() == null ? null : inspection.getUpdatedAt().toString());
        node.put("version", inspection.getVersion());
        return node;
    }

    private JsonNode responseSnapshot(InspectionResponse response) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", response.getId().toString());
        node.put("inspectionId", response.getInspectionId().toString());
        node.put("inspectionItemId", response.getInspectionItemId().toString());
        node.put("valueText", response.getValueText());
        node.put("valueNumber", response.getValueNumber());
        node.put("valueBoolean", response.getValueBoolean());
        node.put("valueDate", response.getValueDate() == null ? null : response.getValueDate().toString());
        node.put("conformity", response.getConformity() == null ? null : response.getConformity().name());
        node.put("observation", response.getObservation());
        node.put("answeredAtDevice", response.getAnsweredAtDevice().toString());
        node.put("answeredAtServer", response.getAnsweredAtServer().toString());
        node.put("updatedAt", response.getUpdatedAt() == null ? null : response.getUpdatedAt().toString());
        node.put("version", response.getVersion());
        return node;
    }

    private JsonNode nonConformitySnapshot(NonConformity nc) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", nc.getId().toString());
        node.put("inspectionId", nc.getInspectionId().toString());
        node.put("title", nc.getTitle());
        node.put("description", nc.getDescription());
        node.put("severity", nc.getSeverity().name());
        node.put("status", nc.getStatus().name());
        node.put("updatedAt", nc.getUpdatedAt() == null ? null : nc.getUpdatedAt().toString());
        node.put("version", nc.getVersion());
        return node;
    }

    private JsonNode evidenceSnapshot(InspectionEvidence evidence) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", evidence.getId().toString());
        node.put("inspectionId", evidence.getInspectionId().toString());
        node.put("mimeType", evidence.getMimeType());
        node.put("sizeBytes", evidence.getSizeBytes());
        node.put("checksum", evidence.getChecksum());
        node.put("description", evidence.getDescription());
        node.put("deletedAt", evidence.getDeletedAt() == null ? null : evidence.getDeletedAt().toString());
        node.put("updatedAt", evidence.getUpdatedAt() == null ? null : evidence.getUpdatedAt().toString());
        node.put("version", evidence.getVersion());
        return node;
    }

    public record ConflictDetails(
            Integer entityVersion,
            String errorCode,
            String errorMessage,
            JsonNode serverState
    ) {
    }
}
