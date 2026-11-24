package com.fieldops.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "ix_audit_events_entity", columnList = "entity_type, entity_id"),
        @Index(name = "ix_audit_events_actor", columnList = "actor_user_id"),
        @Index(name = "ix_audit_insp", columnList = "inspection_id"),
        @Index(name = "ix_audit_occurred", columnList = "occurred_at"),
        @Index(name = "ix_audit_request", columnList = "request_id")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "action", nullable = false, length = 64, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 64, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "before_values", columnDefinition = "TEXT", updatable = false)
    private String beforeValues;

    @Column(name = "after_values", columnDefinition = "TEXT", updatable = false)
    private String afterValues;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "inspection_id", updatable = false)
    private UUID inspectionId;

    @Column(name = "device_occurred_at", updatable = false)
    private Instant deviceOccurredAt;

    @Column(name = "request_id", columnDefinition = "TEXT", updatable = false)
    private String requestId;

    @Column(name = "device_id", updatable = false)
    private UUID deviceId;

    protected AuditEvent() {
    }

    private AuditEvent(
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            String beforeValues,
            String afterValues,
            Instant occurredAt
    ) {
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId must not be null");
        this.beforeValues = beforeValues;
        this.afterValues = afterValues;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static AuditEvent create(
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            String beforeValues,
            String afterValues,
            Instant occurredAt
    ) {
        return new AuditEvent(actorUserId, action, entityType, entityId, beforeValues, afterValues, occurredAt);
    }

    /** Event of an inspection timeline, with the field clock and the request/device correlation. */
    public static AuditEvent create(
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            UUID inspectionId,
            String beforeValues,
            String afterValues,
            Instant occurredAt,
            Instant deviceOccurredAt,
            String requestId,
            UUID deviceId
    ) {
        AuditEvent event = new AuditEvent(actorUserId, action, entityType, entityId, beforeValues, afterValues, occurredAt);
        event.inspectionId = inspectionId;
        event.deviceOccurredAt = deviceOccurredAt;
        event.requestId = requestId;
        event.deviceId = deviceId;
        return event;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getBeforeValues() {
        return beforeValues;
    }

    public String getAfterValues() {
        return afterValues;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getInspectionId() {
        return inspectionId;
    }

    public Instant getDeviceOccurredAt() {
        return deviceOccurredAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }
}
