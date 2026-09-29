package com.fieldops.inspection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mutable logical identity of an inspection template.
 *
 * <p>The template itself holds metadata (title, category, status) while the
 * actual checklist structure (sections and items) lives under
 * {@link InspectionTemplateVersion}.</p>
 */
@Entity
@Table(name = "inspection_templates")
@EntityListeners(AuditingEntityListener.class)
public class InspectionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TemplateStatus status;

    @Column(name = "current_version")
    private Integer currentVersion;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected InspectionTemplate() {
    }

    private InspectionTemplate(String title, String description, String category, UUID createdBy) {
        this.title = requireText(title, "title");
        this.description = normalizeOptional(description);
        this.category = requireText(category, "category");
        this.status = TemplateStatus.DRAFT;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
    }

    /**
     * Factory method to create a new template in DRAFT status.
     */
    public static InspectionTemplate create(String title, String description, String category, UUID createdBy) {
        return new InspectionTemplate(title, description, category, createdBy);
    }

    public void updateMetadata(String title, String description, String category) {
        this.title = requireText(title, "title");
        this.description = normalizeOptional(description);
        this.category = requireText(category, "category");
    }

    public void updateStatus(TemplateStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "status must not be null");
    }

    public void advanceCurrentVersion(int versionNumber) {
        this.currentVersion = versionNumber;
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public TemplateStatus getStatus() {
        return status;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public UUID getCreatedBy() {
        return createdBy;
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

    // ── Helpers ──────────────────────────────────────────────

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
