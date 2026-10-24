package com.fieldops.inspection.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A real execution of a published checklist (documento 10, 10.8.1).
 *
 * <p>Time fields come in device/server pairs: {@code *AtDevice} is the
 * device clock at the moment of the action, {@code *AtServer} is when the
 * API received it. An inspection finished offline at 10h and synced at 16h
 * keeps both (RN-034, RN-074).</p>
 */
@Entity
@Table(name = "inspections")
@EntityListeners(AuditingEntityListener.class)
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "template_version_id", nullable = false, updatable = false)
    private UUID templateVersionId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "supervisor_id")
    private UUID supervisorId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "instructions", length = 2000)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private InspectionPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InspectionStatus status;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "started_at_device")
    private Instant startedAtDevice;

    @Column(name = "started_at_server")
    private Instant startedAtServer;

    @Column(name = "completed_at_device")
    private Instant completedAtDevice;

    @Column(name = "submitted_at_server")
    private Instant submittedAtServer;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "canceled_by")
    private UUID canceledBy;

    @Column(name = "canceled_reason", length = 1000)
    private String canceledReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("sectionOrder ASC, itemOrder ASC")
    private List<InspectionItemSnapshot> itemSnapshots = new ArrayList<>();

    protected Inspection() {
    }

    /**
     * Creates an inspection assigned to a technician and freezes the checklist
     * of the published version as item snapshots (RN-021). Link validation
     * (site of the client, active technician...) belongs to the scheduling use case.
     */
    public static Inspection create(
            InspectionTemplateVersion publishedVersion,
            UUID clientId,
            UUID siteId,
            UUID equipmentId,
            UUID technicianId,
            UUID supervisorId,
            UUID createdBy,
            String title,
            String instructions,
            InspectionPriority priority,
            Instant scheduledFor
    ) {
        Objects.requireNonNull(publishedVersion, "publishedVersion must not be null");
        if (publishedVersion.isDraft()) {
            throw new IllegalArgumentException("an inspection must use a published template version");
        }
        Inspection inspection = new Inspection();
        inspection.templateVersionId = Objects.requireNonNull(publishedVersion.getId(), "publishedVersion id must not be null");
        inspection.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        inspection.siteId = Objects.requireNonNull(siteId, "siteId must not be null");
        inspection.equipmentId = equipmentId;
        inspection.technicianId = Objects.requireNonNull(technicianId, "technicianId must not be null");
        inspection.supervisorId = supervisorId;
        inspection.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        inspection.title = requireText(title, "title");
        inspection.instructions = normalizeOptional(instructions);
        inspection.priority = priority == null ? InspectionPriority.MEDIUM : priority;
        inspection.scheduledFor = Objects.requireNonNull(scheduledFor, "scheduledFor must not be null");
        inspection.status = InspectionStatus.ASSIGNED;

        publishedVersion.getSections().stream()
                .sorted(Comparator.comparingInt(TemplateSection::getDisplayOrder))
                .forEach(section -> section.getItems().stream()
                        .sorted(Comparator.comparingInt(TemplateItem::getDisplayOrder))
                        .forEach(item -> inspection.itemSnapshots.add(
                                InspectionItemSnapshot.copyOf(inspection, section, item)
                        )));
        return inspection;
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getSiteId() {
        return siteId;
    }

    public UUID getEquipmentId() {
        return equipmentId;
    }

    public UUID getTechnicianId() {
        return technicianId;
    }

    public UUID getSupervisorId() {
        return supervisorId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getTitle() {
        return title;
    }

    public String getInstructions() {
        return instructions;
    }

    public InspectionPriority getPriority() {
        return priority;
    }

    public InspectionStatus getStatus() {
        return status;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public Instant getStartedAtDevice() {
        return startedAtDevice;
    }

    public Instant getStartedAtServer() {
        return startedAtServer;
    }

    public Instant getCompletedAtDevice() {
        return completedAtDevice;
    }

    public Instant getSubmittedAtServer() {
        return submittedAtServer;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public UUID getCanceledBy() {
        return canceledBy;
    }

    public String getCanceledReason() {
        return canceledReason;
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

    public List<InspectionItemSnapshot> getItemSnapshots() {
        return List.copyOf(itemSnapshots);
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
