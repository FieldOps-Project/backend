package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionReview;
import com.fieldops.inspection.domain.model.InspectionReviewDecision;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.service.InspectionStateMachine;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionReviewRepository;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    @Mock
    private InspectionRepository inspectionRepository;

    @Mock
    private InspectionReviewRepository reviewRepository;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    private InspectionReviewService service;
    private Inspection inspection;
    private UUID inspectionId;
    private UUID supervisorId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        service = new InspectionReviewService(
                inspectionRepository,
                reviewRepository,
                new InspectionStateMachine(),
                auditEventService,
                authenticatedUserResolver,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        inspectionId = UUID.randomUUID();
        supervisorId = UUID.randomUUID();
        inspection = mockInspection(InspectionStatus.SUBMITTED);
        authentication = new TestingAuthenticationToken("supervisor", "password");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void beginsReviewOfSubmittedInspectionAndAuditsIt() {
        when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));
        when(inspectionRepository.saveAndFlush(inspection)).thenReturn(inspection);

        InspectionTransitionResult result = service.beginReview(inspectionId);

        assertThat(result.previousStatus()).isEqualTo(InspectionStatus.SUBMITTED);
        assertThat(result.inspection().getStatus()).isEqualTo(InspectionStatus.UNDER_REVIEW);
        verify(auditEventService).recordEvent(
                "REVIEW_STARTED", "INSPECTION", inspectionId, "status=SUBMITTED", "status=UNDER_REVIEW"
        );
    }

    @Test
    void refusesToBeginReviewOutsideSubmittedWithConflict() {
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.IN_PROGRESS);
        when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));

        assertThatThrownBy(() -> service.beginReview(inspectionId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_INVALID_TRANSITION");

        verify(inspectionRepository, never()).saveAndFlush(any(Inspection.class));
        verify(auditEventService, never()).recordEvent(any(), any(), any(), any(), any());
    }

    @Test
    void approvalRecordsReviewerDateCommentAndNextCycleThenLocksTheInspection() {
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.UNDER_REVIEW);
        when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));
        when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(supervisorId);
        when(reviewRepository.findLastReviewCycle(inspectionId)).thenReturn(1);
        when(reviewRepository.save(any(InspectionReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionReview review = service.approve(inspectionId, "  Tudo conferido  ");

        assertThat(review.getDecision()).isEqualTo(InspectionReviewDecision.APPROVED);
        assertThat(review.getReviewerId()).isEqualTo(supervisorId);
        assertThat(review.getReviewedAt()).isEqualTo(NOW);
        assertThat(review.getComments()).isEqualTo("Tudo conferido");
        assertThat(review.getReviewCycle()).isEqualTo(2);
        assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.APPROVED);
        assertThat(inspection.getApprovedAt()).isEqualTo(NOW);
        verify(inspectionRepository).saveAndFlush(inspection);
        verify(auditEventService).recordEvent(
                eq("INSPECTION_APPROVED"), eq("INSPECTION"), eq(inspectionId), eq("status=UNDER_REVIEW"),
                argThat(value -> value.startsWith("status=APPROVED;reviewCycle=2;reviewerId=" + supervisorId))
        );
    }

    @Test
    void approvalOutsideUnderReviewIsAConflict() {
        when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));

        assertThatThrownBy(() -> service.approve(inspectionId, null))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_INVALID_TRANSITION");

        verify(reviewRepository, never()).save(any(InspectionReview.class));
    }

    @Test
    void rejectionWithoutReasonIsRefusedBeforeLoadingTheInspection() {
        assertThatThrownBy(() -> service.reject(inspectionId, "  ", null, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_REJECTION_REASON_REQUIRED");

        verify(inspectionRepository, never()).findById(any());
        verify(reviewRepository, never()).save(any(InspectionReview.class));
    }

    @Test
    void rejectionRecordsReasonItemsAndFirstCycle() {
        UUID itemId = UUID.randomUUID();
        Inspection withSnapshots = mockInspection(InspectionStatus.UNDER_REVIEW, itemId);
        when(inspectionRepository.findWithSnapshotsById(inspectionId)).thenReturn(Optional.of(withSnapshots));
        when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(supervisorId);
        when(reviewRepository.findLastReviewCycle(inspectionId)).thenReturn(0);
        when(reviewRepository.save(any(InspectionReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionReview review = service.reject(
                inspectionId, "Foto ilegivel", null, List.of(itemId, itemId)
        );

        assertThat(review.getDecision()).isEqualTo(InspectionReviewDecision.REJECTED);
        assertThat(review.getReason()).isEqualTo("Foto ilegivel");
        assertThat(review.getItemsToCorrect()).containsExactly(itemId);
        assertThat(review.getReviewCycle()).isEqualTo(1);
        assertThat(withSnapshots.getStatus()).isEqualTo(InspectionStatus.REJECTED);
        verify(auditEventService).recordEvent(
                eq("INSPECTION_REJECTED"), eq("INSPECTION"), eq(inspectionId), eq("status=UNDER_REVIEW"),
                argThat(value -> value.contains("reason=Foto ilegivel") && value.contains(itemId.toString()))
        );
    }

    @Test
    void rejectionWithoutItemsDoesNotLoadSnapshots() {
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.UNDER_REVIEW);
        when(inspectionRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));
        when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(supervisorId);
        when(reviewRepository.save(any(InspectionReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionReview review = service.reject(inspectionId, "Refazer tudo", null, null);

        assertThat(review.getItemsToCorrect()).isEmpty();
        verify(inspectionRepository, never()).findWithSnapshotsById(any());
    }

    @Test
    void rejectionWithItemOutsideTheSnapshotIsRefused() {
        Inspection withSnapshots = mockInspection(InspectionStatus.UNDER_REVIEW, UUID.randomUUID());
        when(inspectionRepository.findWithSnapshotsById(inspectionId)).thenReturn(Optional.of(withSnapshots));
        UUID foreignItem = UUID.randomUUID();

        assertThatThrownBy(() -> service.reject(inspectionId, "Foto ilegivel", null, List.of(foreignItem)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_ITEM_NOT_IN_SNAPSHOT");

        verify(reviewRepository, never()).save(any(InspectionReview.class));
        assertThat(withSnapshots.getStatus()).isEqualTo(InspectionStatus.UNDER_REVIEW);
    }

    @Test
    void listsEveryCycleOfAnExistingInspection() {
        InspectionReview rejected = InspectionReview.reject(
                inspectionId, supervisorId, "Foto ilegivel", null, NOW, 1, List.of()
        );
        InspectionReview approved = InspectionReview.approve(inspectionId, supervisorId, null, NOW, 2);
        when(inspectionRepository.existsById(inspectionId)).thenReturn(true);
        when(reviewRepository.findAllByInspectionIdOrderByReviewCycleAsc(inspectionId))
                .thenReturn(List.of(rejected, approved));

        assertThat(service.findAll(inspectionId))
                .extracting(InspectionReview::getReviewCycle, InspectionReview::getDecision)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, InspectionReviewDecision.REJECTED),
                        org.assertj.core.groups.Tuple.tuple(2, InspectionReviewDecision.APPROVED)
                );
    }

    private Inspection mockInspection(InspectionStatus status, UUID... snapshotIds) {
        Inspection value = org.springframework.beans.BeanUtils.instantiateClass(Inspection.class);
        ReflectionTestUtils.setField(value, "id", inspectionId);
        ReflectionTestUtils.setField(value, "status", status);
        List<InspectionItemSnapshot> snapshots = new java.util.ArrayList<>();
        for (UUID snapshotId : snapshotIds) {
            InspectionItemSnapshot snapshot = mock(InspectionItemSnapshot.class);
            when(snapshot.getId()).thenReturn(snapshotId);
            snapshots.add(snapshot);
        }
        ReflectionTestUtils.setField(value, "itemSnapshots", snapshots);
        return value;
    }
}
