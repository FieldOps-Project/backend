package com.fieldops.inspection.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One supervisor decision on an inspection (documento 10, 10.11.1).
 *
 * <p>Each review cycle is its own immutable record, so earlier rounds stay
 * available after a correction (RN-083).</p>
 */
@Entity
@Table(name = "inspection_reviews")
@EntityListeners(AuditingEntityListener.class)
public class InspectionReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "inspection_id", nullable = false, updatable = false)
    private UUID inspectionId;

    @Column(name = "reviewer_id", nullable = false, updatable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16, updatable = false)
    private InspectionReviewDecision decision;

    @Column(name = "reason", length = 2000, updatable = false)
    private String reason;

    @Column(name = "comments", length = 2000, updatable = false)
    private String comments;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    @Column(name = "review_cycle", nullable = false, updatable = false)
    private int reviewCycle;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "inspection_review_items", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "inspection_item_id", nullable = false)
    private Set<UUID> itemsToCorrect = new LinkedHashSet<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InspectionReview() {
    }

    public static InspectionReview approve(
            UUID inspectionId,
            UUID reviewerId,
            String comments,
            Instant reviewedAt,
            int reviewCycle
    ) {
        return create(inspectionId, reviewerId, InspectionReviewDecision.APPROVED, null, comments,
                reviewedAt, reviewCycle, List.of());
    }

    public static InspectionReview reject(
            UUID inspectionId,
            UUID reviewerId,
            String reason,
            String comments,
            Instant reviewedAt,
            int reviewCycle,
            List<UUID> itemsToCorrect
    ) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return create(inspectionId, reviewerId, InspectionReviewDecision.REJECTED, reason, comments,
                reviewedAt, reviewCycle, itemsToCorrect);
    }

    private static InspectionReview create(
            UUID inspectionId,
            UUID reviewerId,
            InspectionReviewDecision decision,
            String reason,
            String comments,
            Instant reviewedAt,
            int reviewCycle,
            List<UUID> itemsToCorrect
    ) {
        if (reviewCycle < 1) {
            throw new IllegalArgumentException("reviewCycle must be positive");
        }
        InspectionReview review = new InspectionReview();
        review.inspectionId = Objects.requireNonNull(inspectionId, "inspectionId must not be null");
        review.reviewerId = Objects.requireNonNull(reviewerId, "reviewerId must not be null");
        review.decision = decision;
        review.reason = normalizeOptional(reason);
        review.comments = normalizeOptional(comments);
        review.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        review.reviewCycle = reviewCycle;
        review.itemsToCorrect.addAll(itemsToCorrect);
        return review;
    }

    public UUID getId() { return id; }
    public UUID getInspectionId() { return inspectionId; }
    public UUID getReviewerId() { return reviewerId; }
    public InspectionReviewDecision getDecision() { return decision; }
    public String getReason() { return reason; }
    public String getComments() { return comments; }
    public Instant getReviewedAt() { return reviewedAt; }
    public int getReviewCycle() { return reviewCycle; }
    public List<UUID> getItemsToCorrect() { return List.copyOf(itemsToCorrect); }
    public Instant getCreatedAt() { return createdAt; }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
