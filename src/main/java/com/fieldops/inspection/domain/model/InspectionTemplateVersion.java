package com.fieldops.inspection.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, numbered publication of an {@link InspectionTemplate}.
 *
 * <p>Sections and items always belong to a <em>version</em>, never directly
 * to the logical template — this guarantees that historical inspections
 * display exactly the questions the technician originally answered.</p>
 *
 * <p><strong>Draft strategy (see ADR):</strong> a draft is represented by
 * {@code versionNumber = 0} and {@code publishedAt = null}.</p>
 */
@Entity
@Table(name = "inspection_template_versions")
@EntityListeners(AuditingEntityListener.class)
public class InspectionTemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private InspectionTemplate template;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "title_snapshot", nullable = false, length = 255)
    private String titleSnapshot;

    @Column(name = "description_snapshot", length = 1000)
    private String descriptionSnapshot;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "active_for_new_inspections", nullable = false)
    private boolean activeForNewInspections;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "templateVersion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<TemplateSection> sections = new ArrayList<>();

    protected InspectionTemplateVersion() {
    }

    private InspectionTemplateVersion(
            InspectionTemplate template,
            int versionNumber,
            String titleSnapshot,
            String descriptionSnapshot
    ) {
        this.template = Objects.requireNonNull(template, "template must not be null");
        this.versionNumber = versionNumber;
        this.titleSnapshot = requireText(titleSnapshot, "titleSnapshot");
        this.descriptionSnapshot = normalizeOptional(descriptionSnapshot);
        this.activeForNewInspections = false;
    }

    /**
     * Creates a new draft version (version_number = 0, published_at = null).
     */
    public static InspectionTemplateVersion createDraft(
            InspectionTemplate template,
            String titleSnapshot,
            String descriptionSnapshot
    ) {
        return new InspectionTemplateVersion(template, 0, titleSnapshot, descriptionSnapshot);
    }

    /**
     * Creates a numbered, published version.
     */
    public static InspectionTemplateVersion createPublished(
            InspectionTemplate template,
            int versionNumber,
            String titleSnapshot,
            String descriptionSnapshot,
            UUID publishedBy
    ) {
        if (versionNumber < 1) {
            throw new IllegalArgumentException("published version number must be >= 1");
        }
        InspectionTemplateVersion v = new InspectionTemplateVersion(
                template, versionNumber, titleSnapshot, descriptionSnapshot
        );
        v.publishedBy = Objects.requireNonNull(publishedBy, "publishedBy must not be null");
        v.publishedAt = Instant.now();
        v.activeForNewInspections = true;
        return v;
    }

    public void addSection(TemplateSection section) {
        sections.add(section);
    }

    public boolean isDraft() {
        return publishedAt == null;
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public InspectionTemplate getTemplate() {
        return template;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getTitleSnapshot() {
        return titleSnapshot;
    }

    public String getDescriptionSnapshot() {
        return descriptionSnapshot;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public boolean isActiveForNewInspections() {
        return activeForNewInspections;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<TemplateSection> getSections() {
        return sections;
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
