package com.fieldops.inspection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Frozen copy of one checklist item as it was when the inspection was
 * created (documento 10, 10.8.3; RN-021).
 *
 * <p>Text and rules are copied, never referenced: {@code sourceTemplateItemId}
 * is only a historical trace and must not be used to read the current text.
 * Rows are immutable here and in the database.</p>
 */
@Entity
@Immutable
@Table(name = "inspection_item_snapshots")
@EntityListeners(AuditingEntityListener.class)
public class InspectionItemSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false, updatable = false)
    private Inspection inspection;

    @Column(name = "source_template_item_id", updatable = false)
    private UUID sourceTemplateItemId;

    @Column(name = "section_title", nullable = false, length = 255)
    private String sectionTitle;

    @Column(name = "section_description", length = 1000)
    private String sectionDescription;

    @Column(name = "section_order", nullable = false)
    private int sectionOrder;

    @Column(name = "item_code", length = 50)
    private String itemCode;

    @Column(name = "item_title", nullable = false, length = 255)
    private String itemTitle;

    @Column(name = "item_description", length = 1000)
    private String itemDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false, length = 32)
    private ResponseType responseType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_json", nullable = false)
    private String rulesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json")
    private String optionsJson;

    @Column(name = "item_order", nullable = false)
    private int itemOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InspectionItemSnapshot() {
    }

    static InspectionItemSnapshot copyOf(Inspection inspection, TemplateSection section, TemplateItem item) {
        InspectionItemSnapshot snapshot = new InspectionItemSnapshot();
        snapshot.inspection = inspection;
        snapshot.sourceTemplateItemId = item.getId();
        snapshot.sectionTitle = section.getTitle();
        snapshot.sectionDescription = section.getDescription();
        snapshot.sectionOrder = section.getDisplayOrder();
        snapshot.itemCode = item.getCode();
        snapshot.itemTitle = item.getTitle();
        snapshot.itemDescription = item.getDescription();
        snapshot.responseType = item.getResponseType();
        snapshot.required = item.isRequired();
        snapshot.rulesJson = "{\"observationRequiredOnFailure\":" + item.isObservationRequiredOnFailure()
                + ",\"evidenceRequiredOnFailure\":" + item.isEvidenceRequiredOnFailure() + "}";
        snapshot.optionsJson = item.getOptionsJson();
        snapshot.itemOrder = item.getDisplayOrder();
        return snapshot;
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public Inspection getInspection() {
        return inspection;
    }

    public UUID getSourceTemplateItemId() {
        return sourceTemplateItemId;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public String getSectionDescription() {
        return sectionDescription;
    }

    public int getSectionOrder() {
        return sectionOrder;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public String getItemDescription() {
        return itemDescription;
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public boolean isRequired() {
        return required;
    }

    /** {@code {"observationRequiredOnFailure":bool,"evidenceRequiredOnFailure":bool}} */
    public String getRulesJson() {
        return rulesJson;
    }

    /** JSON array of choices for SINGLE_CHOICE items; {@code null} otherwise. */
    public String getOptionsJson() {
        return optionsJson;
    }

    public int getItemOrder() {
        return itemOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
