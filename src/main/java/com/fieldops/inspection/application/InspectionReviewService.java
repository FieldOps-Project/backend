package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionAction;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionReview;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.service.InspectionStateMachine;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionReviewRepository;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.domain.exception.Violation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InspectionReviewService {

    private static final String INSPECTION_ENTITY = "INSPECTION";

    private final ObjectProvider<InspectionRepository> inspectionRepositoryProvider;
    private final ObjectProvider<InspectionReviewRepository> reviewRepositoryProvider;
    private final InspectionRepository directInspectionRepository;
    private final InspectionReviewRepository directReviewRepository;
    private final InspectionStateMachine stateMachine;
    private final AuditEventService auditEventService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final Clock clock;

    @Autowired
    public InspectionReviewService(
            ObjectProvider<InspectionRepository> inspectionRepositoryProvider,
            ObjectProvider<InspectionReviewRepository> reviewRepositoryProvider,
            InspectionStateMachine stateMachine,
            AuditEventService auditEventService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepositoryProvider = inspectionRepositoryProvider;
        this.reviewRepositoryProvider = reviewRepositoryProvider;
        this.directInspectionRepository = null;
        this.directReviewRepository = null;
        this.stateMachine = stateMachine;
        this.auditEventService = auditEventService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.clock = Clock.systemUTC();
    }

    InspectionReviewService(
            InspectionRepository inspectionRepository,
            InspectionReviewRepository reviewRepository,
            InspectionStateMachine stateMachine,
            AuditEventService auditEventService,
            AuthenticatedUserResolver authenticatedUserResolver,
            Clock clock
    ) {
        this.inspectionRepositoryProvider = null;
        this.reviewRepositoryProvider = null;
        this.directInspectionRepository = inspectionRepository;
        this.directReviewRepository = reviewRepository;
        this.stateMachine = stateMachine;
        this.auditEventService = auditEventService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_REVIEW)
    public List<InspectionReview> findAll(UUID inspectionId) {
        if (!inspectionRepository().existsById(inspectionId)) {
            throw ResourceNotFoundException.of("Inspection", inspectionId);
        }
        return reviewRepository().findAllByInspectionIdOrderByReviewCycleAsc(inspectionId);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_REVIEW)
    public InspectionTransitionResult beginReview(UUID inspectionId) {
        Inspection inspection = findRequired(inspectionId);
        stateMachine.assertTransitionValid(inspection.getStatus(), InspectionAction.START_REVIEW);

        InspectionStatus previousStatus = inspection.getStatus();
        inspection.beginReview();
        Inspection saved = inspectionRepository().saveAndFlush(inspection);
        auditEventService.recordEvent(
                "REVIEW_STARTED",
                INSPECTION_ENTITY,
                saved.getId(),
                "status=" + previousStatus,
                "status=" + saved.getStatus()
        );
        return new InspectionTransitionResult(saved, previousStatus);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_REVIEW)
    public InspectionReview approve(UUID inspectionId, String comments) {
        Inspection inspection = findRequired(inspectionId);
        stateMachine.assertTransitionValid(inspection.getStatus(), InspectionAction.APPROVE);

        Instant reviewedAt = clock.instant();
        InspectionStatus previousStatus = inspection.getStatus();
        InspectionReview review = reviewRepository().save(InspectionReview.approve(
                inspectionId, currentUserId(), comments, reviewedAt, nextReviewCycle(inspectionId)
        ));
        inspection.approve(reviewedAt);
        inspectionRepository().saveAndFlush(inspection);
        auditEventService.recordEvent(
                "INSPECTION_APPROVED",
                INSPECTION_ENTITY,
                inspectionId,
                "status=" + previousStatus,
                auditState(inspection, review)
        );
        return review;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_REVIEW)
    public InspectionReview reject(UUID inspectionId, String reason, String comments, List<UUID> itemsToCorrect) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(
                    "INSPECTION_REJECTION_REASON_REQUIRED",
                    "A rejection must have a reason",
                    List.of(new Violation("reason", "must not be blank"))
            );
        }
        List<UUID> items = itemsToCorrect == null ? List.of() : List.copyOf(new LinkedHashSet<>(itemsToCorrect));
        // Snapshots are only fetched when there are items to check against them.
        Inspection inspection = items.isEmpty()
                ? findRequired(inspectionId)
                : inspectionRepository().findWithSnapshotsById(inspectionId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
        stateMachine.assertTransitionValid(inspection.getStatus(), InspectionAction.REJECT);
        validateItemsToCorrect(inspection, items);

        InspectionStatus previousStatus = inspection.getStatus();
        InspectionReview review = reviewRepository().save(InspectionReview.reject(
                inspectionId, currentUserId(), reason, comments, clock.instant(), nextReviewCycle(inspectionId), items
        ));
        inspection.reject();
        inspectionRepository().saveAndFlush(inspection);
        auditEventService.recordEvent(
                "INSPECTION_REJECTED",
                INSPECTION_ENTITY,
                inspectionId,
                "status=" + previousStatus,
                auditState(inspection, review)
        );
        return review;
    }

    private void validateItemsToCorrect(Inspection inspection, List<UUID> items) {
        if (items.isEmpty()) {
            return;
        }
        Set<UUID> snapshotIds = inspection.getItemSnapshots().stream()
                .map(InspectionItemSnapshot::getId)
                .collect(Collectors.toSet());
        List<Violation> violations = items.stream()
                .filter(itemId -> !snapshotIds.contains(itemId))
                .map(itemId -> new Violation("itemsToCorrect[" + itemId + "]", "The item is not part of this inspection"))
                .toList();
        if (!violations.isEmpty()) {
            throw new BusinessRuleException(
                    "INSPECTION_ITEM_NOT_IN_SNAPSHOT",
                    "Items to correct must belong to the inspection snapshot",
                    violations
            );
        }
    }

    /** The inspection version check on save serializes concurrent decisions on the same inspection. */
    private int nextReviewCycle(UUID inspectionId) {
        return reviewRepository().findLastReviewCycle(inspectionId) + 1;
    }

    private Inspection findRequired(UUID inspectionId) {
        return inspectionRepository().findById(inspectionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
    }

    private InspectionRepository inspectionRepository() {
        return directInspectionRepository != null
                ? directInspectionRepository : inspectionRepositoryProvider.getObject();
    }

    private InspectionReviewRepository reviewRepository() {
        return directReviewRepository != null ? directReviewRepository : reviewRepositoryProvider.getObject();
    }

    private UUID currentUserId() {
        return authenticatedUserResolver.requireUserId(SecurityContextHolder.getContext().getAuthentication());
    }

    private String auditState(Inspection inspection, InspectionReview review) {
        return "status=" + inspection.getStatus()
                + ";reviewCycle=" + review.getReviewCycle()
                + ";reviewerId=" + review.getReviewerId()
                + ";reason=" + review.getReason()
                + ";comments=" + review.getComments()
                + ";itemsToCorrect=" + review.getItemsToCorrect();
    }
}
