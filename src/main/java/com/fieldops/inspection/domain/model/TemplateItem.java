package com.fieldops.inspection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * A single checklist item within a {@link TemplateSection}.
 *
 * <p>Each item defines a question or field that the technician must answer
 * during an inspection. The {@code responseType} constrains what kind of
 * answer is expected (RN-016), and {@code displayOrder} guarantees explicit
 * ordering within the section (RN-017).</p>
 */
@Entity
@Table(name = "template_items")
public class TemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false, updatable = false)
    private TemplateSection section;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false, length = 32)
    private ResponseType responseType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "observation_required_on_failure", nullable = false)
    private boolean observationRequiredOnFailure;

    @Column(name = "evidence_required_on_failure", nullable = false)
    private boolean evidenceRequiredOnFailure;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected TemplateItem() {
    }

    private TemplateItem(
            TemplateSection section,
            String code,
            String title,
            String description,
            ResponseType responseType,
            boolean required,
            boolean observationRequiredOnFailure,
            boolean evidenceRequiredOnFailure,
            String optionsJson,
            int displayOrder
    ) {
        this.section = Objects.requireNonNull(section, "section must not be null");
        this.code = normalizeOptional(code);
        this.title = requireText(title, "title");
        this.description = normalizeOptional(description);
        this.responseType = Objects.requireNonNull(responseType, "responseType must not be null");
        this.required = required;
        this.observationRequiredOnFailure = observationRequiredOnFailure;
        this.evidenceRequiredOnFailure = evidenceRequiredOnFailure;
        this.optionsJson = normalizeOptional(optionsJson);
        this.displayOrder = displayOrder;
    }

    public static TemplateItem create(
            TemplateSection section,
            String code,
            String title,
            String description,
            ResponseType responseType,
            boolean required,
            boolean observationRequiredOnFailure,
            boolean evidenceRequiredOnFailure,
            String optionsJson,
            int displayOrder
    ) {
        return new TemplateItem(
                section, code, title, description, responseType,
                required, observationRequiredOnFailure, evidenceRequiredOnFailure,
                optionsJson, displayOrder
        );
    }

    public void updateMetadata(
            String code,
            String title,
            String description,
            ResponseType responseType,
            boolean required,
            boolean observationRequiredOnFailure,
            boolean evidenceRequiredOnFailure,
            String optionsJson
    ) {
        this.code = normalizeOptional(code);
        this.title = requireText(title, "title");
        this.description = normalizeOptional(description);
        this.responseType = Objects.requireNonNull(responseType, "responseType must not be null");
        this.required = required;
        this.observationRequiredOnFailure = observationRequiredOnFailure;
        this.evidenceRequiredOnFailure = evidenceRequiredOnFailure;
        this.optionsJson = normalizeOptional(optionsJson);
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public TemplateSection getSection() {
        return section;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isObservationRequiredOnFailure() {
        return observationRequiredOnFailure;
    }

    public boolean isEvidenceRequiredOnFailure() {
        return evidenceRequiredOnFailure;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public int getDisplayOrder() {
        return displayOrder;
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
