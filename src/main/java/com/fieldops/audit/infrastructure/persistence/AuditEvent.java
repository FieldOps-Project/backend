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
        @Index(name = "ix_audit_events_actor", columnList = "actor_user_id")
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
}
