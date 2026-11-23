package com.fieldops.inspection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "non_conformities")
@EntityListeners(AuditingEntityListener.class)
public class NonConformity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "inspection_id", nullable = false, updatable = false)
    private UUID inspectionId;

    @Column(name = "inspection_item_id", updatable = false)
    private UUID inspectionItemId;

    @Column(name = "response_id", updatable = false)
    private UUID responseId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private NonConformitySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NonConformityStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at_device", nullable = false, updatable = false)
    private Instant createdAtDevice;

    @Column(name = "server_received_at", nullable = false, updatable = false)
    private Instant serverReceivedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected NonConformity() {
    }

    public static NonConformity create(
            UUID id,
            UUID inspectionId,
            UUID inspectionItemId,
            UUID responseId,
            String title,
            String description,
            NonConformitySeverity severity,
            UUID createdBy,
            Instant createdAtDevice,
            Instant serverReceivedAt
    ) {
        NonConformity nonConformity = new NonConformity();
        nonConformity.id = Objects.requireNonNull(id, "id must not be null");
        nonConformity.inspectionId = Objects.requireNonNull(inspectionId, "inspectionId must not be null");
        nonConformity.inspectionItemId = inspectionItemId;
        nonConformity.responseId = responseId;
        nonConformity.title = requireText(title, "title");
        nonConformity.description = requireText(description, "description");
        nonConformity.severity = Objects.requireNonNull(severity, "severity must not be null");
        nonConformity.status = NonConformityStatus.OPEN;
        nonConformity.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        nonConformity.createdAtDevice = Objects.requireNonNull(createdAtDevice, "createdAtDevice must not be null");
        nonConformity.serverReceivedAt = Objects.requireNonNull(serverReceivedAt, "serverReceivedAt must not be null");
        return nonConformity;
    }

    public void updateDetails(String title, String description, NonConformitySeverity severity) {
        this.title = requireText(title, "title");
        this.description = requireText(description, "description");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
    }

    public UUID getId() { return id; }
    public UUID getInspectionId() { return inspectionId; }
    public UUID getInspectionItemId() { return inspectionItemId; }
    public UUID getResponseId() { return responseId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public NonConformitySeverity getSeverity() { return severity; }
    public NonConformityStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAtDevice() { return createdAtDevice; }
    public Instant getServerReceivedAt() { return serverReceivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
