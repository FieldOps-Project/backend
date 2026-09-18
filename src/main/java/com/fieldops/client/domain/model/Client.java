package com.fieldops.client.domain.model;

import com.fieldops.shared.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "clients")
@EntityListeners(AuditingEntityListener.class)
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "document", length = 14)
    private String document;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClientStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected Client() {
    }

    private Client(
            String name,
            String legalName,
            String document,
            String email,
            String phone,
            ClientStatus status
    ) {
        this.name = requireText(name, "name");
        this.legalName = normalizeOptional(legalName);
        this.document = normalizeOptional(document);
        this.email = normalizeOptional(email);
        this.phone = normalizeOptional(phone);
        this.status = status == null ? ClientStatus.ACTIVE : status;
    }

    public static Client create(
            String name,
            String legalName,
            String document,
            String email,
            String phone,
            ClientStatus status
    ) {
        return new Client(name, legalName, document, email, phone, status);
    }

    public void updateProfile(
            String name,
            String legalName,
            String document,
            String email,
            String phone
    ) {
        this.name = requireText(name, "name");
        this.legalName = normalizeOptional(legalName);
        this.document = normalizeOptional(document);
        this.email = normalizeOptional(email);
        this.phone = normalizeOptional(phone);
    }

    public void updateStatus(ClientStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public boolean isActive() {
        return status == ClientStatus.ACTIVE;
    }

    public void ensureAvailableForScheduling() {
        if (!isActive()) {
            throw new BusinessRuleException(
                    "CLIENT_INACTIVE",
                    "Inactive clients cannot be selected for new inspections"
            );
        }
    }

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = ClientStatus.ACTIVE;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getDocument() {
        return document;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public ClientStatus getStatus() {
        return status;
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
