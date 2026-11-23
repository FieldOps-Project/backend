package com.fieldops.inspection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inspection_responses")
@EntityListeners(AuditingEntityListener.class)
public class InspectionResponse {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "inspection_id", nullable = false, updatable = false)
    private UUID inspectionId;

    @Column(name = "inspection_item_id", nullable = false, updatable = false)
    private UUID inspectionItemId;

    @Column(name = "answered_by", nullable = false, updatable = false)
    private UUID answeredBy;

    @Column(name = "value_text", length = 2000)
    private String valueText;

    @Column(name = "value_number", precision = 24, scale = 8)
    private BigDecimal valueNumber;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_json")
    private String valueJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "conformity", length = 20)
    private InspectionResponseConformity conformity;

    @Column(name = "observation", length = 2000)
    private String observation;

    @Column(name = "answered_at_device", nullable = false)
    private Instant answeredAtDevice;

    @Column(name = "answered_at_server", nullable = false)
    private Instant answeredAtServer;

    @Column(name = "base_version", nullable = false)
    private int baseVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected InspectionResponse() {
    }

    public static InspectionResponse create(
            UUID id,
            UUID inspectionId,
            UUID inspectionItemId,
            UUID answeredBy,
            InspectionResponseValue value,
            String observation,
            Instant answeredAtDevice,
            Instant answeredAtServer,
            int baseVersion
    ) {
        InspectionResponse response = new InspectionResponse();
        response.id = Objects.requireNonNull(id, "id must not be null");
        response.inspectionId = Objects.requireNonNull(inspectionId, "inspectionId must not be null");
        response.inspectionItemId = Objects.requireNonNull(inspectionItemId, "inspectionItemId must not be null");
        response.answeredBy = Objects.requireNonNull(answeredBy, "answeredBy must not be null");
        response.applyValue(value, observation, answeredAtDevice, answeredAtServer, baseVersion);
        return response;
    }

    public void update(
            UUID answeredBy,
            InspectionResponseValue value,
            String observation,
            Instant answeredAtDevice,
            Instant answeredAtServer,
            int baseVersion
    ) {
        this.answeredBy = Objects.requireNonNull(answeredBy, "answeredBy must not be null");
        applyValue(value, observation, answeredAtDevice, answeredAtServer, baseVersion);
    }

    private void applyValue(
            InspectionResponseValue value,
            String observation,
            Instant answeredAtDevice,
            Instant answeredAtServer,
            int baseVersion
    ) {
        this.valueText = value.valueText();
        this.valueNumber = value.valueNumber();
        this.valueBoolean = value.valueBoolean();
        this.valueDate = value.valueDate();
        this.valueJson = value.valueJson();
        this.conformity = value.conformity();
        this.observation = normalizeObservation(observation);
        this.answeredAtDevice = Objects.requireNonNull(answeredAtDevice, "answeredAtDevice must not be null");
        this.answeredAtServer = Objects.requireNonNull(answeredAtServer, "answeredAtServer must not be null");
        this.baseVersion = baseVersion;
    }

    private static String normalizeObservation(String observation) {
        return observation == null || observation.isBlank() ? null : observation.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getInspectionId() {
        return inspectionId;
    }

    public UUID getInspectionItemId() {
        return inspectionItemId;
    }

    public UUID getAnsweredBy() {
        return answeredBy;
    }

    public String getValueText() {
        return valueText;
    }

    public BigDecimal getValueNumber() {
        return valueNumber;
    }

    public Boolean getValueBoolean() {
        return valueBoolean;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public String getValueJson() {
        return valueJson;
    }

    public InspectionResponseConformity getConformity() {
        return conformity;
    }

    public String getObservation() {
        return observation;
    }

    public Instant getAnsweredAtDevice() {
        return answeredAtDevice;
    }

    public Instant getAnsweredAtServer() {
        return answeredAtServer;
    }

    public int getBaseVersion() {
        return baseVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
