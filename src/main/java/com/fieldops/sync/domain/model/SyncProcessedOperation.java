package com.fieldops.sync.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_processed_operations")
public class SyncProcessedOperation {

    @Id
    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Column(name = "device_id", nullable = false, updatable = false)
    private UUID deviceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 32)
    private SyncOperationResultStatus resultStatus;

    @Column(name = "entity_version")
    private Integer entityVersion;

    @Column(name = "result_code", length = 100)
    private String resultCode;

    @Column(name = "result_message", length = 500)
    private String resultMessage;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected SyncProcessedOperation() {
    }

    private SyncProcessedOperation(
            UUID operationId,
            UUID deviceId,
            UUID userId,
            String entityType,
            UUID entityId,
            SyncOperationResultStatus resultStatus,
            Integer entityVersion,
            String resultCode,
            String resultMessage,
            Instant processedAt
    ) {
        this.operationId = operationId;
        this.deviceId = deviceId;
        this.userId = userId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.resultStatus = resultStatus;
        this.entityVersion = entityVersion;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.processedAt = processedAt;
    }

    public static SyncProcessedOperation record(
            UUID operationId,
            UUID deviceId,
            UUID userId,
            String entityType,
            UUID entityId,
            SyncOperationResultStatus resultStatus,
            Integer entityVersion,
            String resultCode,
            String resultMessage,
            Instant processedAt
    ) {
        return new SyncProcessedOperation(
                operationId, deviceId, userId, entityType, entityId, resultStatus,
                entityVersion, resultCode, resultMessage, processedAt
        );
    }

    public UUID getOperationId() {
        return operationId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public SyncOperationResultStatus getResultStatus() {
        return resultStatus;
    }

    public Integer getEntityVersion() {
        return entityVersion;
    }

    public String getResultCode() {
        return resultCode;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
