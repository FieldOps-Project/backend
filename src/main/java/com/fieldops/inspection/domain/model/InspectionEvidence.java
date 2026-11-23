package com.fieldops.inspection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inspection_evidences")
@EntityListeners(AuditingEntityListener.class)
public class InspectionEvidence {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "inspection_id", nullable = false, updatable = false)
    private UUID inspectionId;

    @Column(name = "response_id", updatable = false)
    private UUID responseId;

    @Column(name = "non_conformity_id", updatable = false)
    private UUID nonConformityId;

    @Column(name = "type", nullable = false, length = 32, updatable = false)
    private String type;

    @Column(name = "storage_key", nullable = false, length = 300, unique = true, updatable = false)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 64, updatable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "checksum", nullable = false, length = 64, updatable = false)
    private String checksum;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "latitude", precision = 9, scale = 6, updatable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6, updatable = false)
    private BigDecimal longitude;

    @Column(name = "accuracy_meters", precision = 10, scale = 2, updatable = false)
    private BigDecimal accuracyMeters;

    @Column(name = "captured_at_device", nullable = false, updatable = false)
    private Instant capturedAtDevice;

    @Column(name = "server_received_at", nullable = false, updatable = false)
    private Instant serverReceivedAt;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected InspectionEvidence() {
    }

    public static InspectionEvidence create(
            UUID id,
            UUID inspectionId,
            UUID responseId,
            UUID nonConformityId,
            String type,
            String storageKey,
            String mimeType,
            long sizeBytes,
            String checksum,
            String description,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyMeters,
            Instant capturedAtDevice,
            Instant serverReceivedAt,
            Instant uploadedAt,
            UUID createdBy
    ) {
        InspectionEvidence evidence = new InspectionEvidence();
        evidence.id = Objects.requireNonNull(id, "id must not be null");
        evidence.inspectionId = Objects.requireNonNull(inspectionId, "inspectionId must not be null");
        evidence.responseId = responseId;
        evidence.nonConformityId = nonConformityId;
        evidence.type = requireText(type, "type");
        evidence.storageKey = requireText(storageKey, "storageKey");
        evidence.mimeType = requireText(mimeType, "mimeType");
        evidence.sizeBytes = sizeBytes;
        evidence.checksum = requireText(checksum, "checksum");
        evidence.description = normalizeOptional(description);
        evidence.latitude = latitude;
        evidence.longitude = longitude;
        evidence.accuracyMeters = accuracyMeters;
        evidence.capturedAtDevice = Objects.requireNonNull(capturedAtDevice, "capturedAtDevice must not be null");
        evidence.serverReceivedAt = Objects.requireNonNull(serverReceivedAt, "serverReceivedAt must not be null");
        evidence.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
        evidence.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        return evidence;
    }

    public void remove(UUID deletedBy, Instant deletedAt) {
        this.deletedBy = Objects.requireNonNull(deletedBy, "deletedBy must not be null");
        this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt must not be null");
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public UUID getInspectionId() { return inspectionId; }
    public UUID getResponseId() { return responseId; }
    public UUID getNonConformityId() { return nonConformityId; }
    public String getType() { return type; }
    public String getStorageKey() { return storageKey; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getChecksum() { return checksum; }
    public String getDescription() { return description; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getAccuracyMeters() { return accuracyMeters; }
    public Instant getCapturedAtDevice() { return capturedAtDevice; }
    public Instant getServerReceivedAt() { return serverReceivedAt; }
    public Instant getUploadedAt() { return uploadedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public UUID getDeletedBy() { return deletedBy; }
    public int getVersion() { return version; }
}
