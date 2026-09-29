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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A section within an {@link InspectionTemplateVersion}.
 *
 * <p>Sections belong to a <em>version</em>, never directly to the logical
 * template. The {@code displayOrder} field guarantees explicit ordering
 * (RN-017) and has a unique constraint per version in the database.</p>
 */
@Entity
@Table(name = "template_sections")
public class TemplateSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_version_id", nullable = false, updatable = false)
    private InspectionTemplateVersion templateVersion;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<TemplateItem> items = new ArrayList<>();

    protected TemplateSection() {
    }

    private TemplateSection(
            InspectionTemplateVersion templateVersion,
            String title,
            String description,
            int displayOrder
    ) {
        this.templateVersion = Objects.requireNonNull(templateVersion, "templateVersion must not be null");
        this.title = requireText(title, "title");
        this.description = normalizeOptional(description);
        this.displayOrder = displayOrder;
    }

    public static TemplateSection create(
            InspectionTemplateVersion templateVersion,
            String title,
            String description,
            int displayOrder
    ) {
        return new TemplateSection(templateVersion, title, description, displayOrder);
    }

    public void addItem(TemplateItem item) {
        items.add(item);
    }

    public void updateMetadata(String title, String description) {
        this.title = requireText(title, "title");
        this.description = normalizeOptional(description);
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public InspectionTemplateVersion getTemplateVersion() {
        return templateVersion;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public List<TemplateItem> getItems() {
        return items;
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
