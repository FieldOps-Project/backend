package com.fieldops.inspection.application;

import tools.jackson.databind.ObjectMapper;
import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.inspection.domain.model.InspectionResponseValue;
import com.fieldops.inspection.domain.model.InspectionReview;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionReviewRepository;
import com.fieldops.inspection.presentation.dto.SaveInspectionResponseRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InspectionResponseCorrectionTest {

    private static final Instant DEVICE_TIME = Instant.parse("2026-11-20T10:00:00Z");

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final InspectionResponseRepository responseRepository = mock(InspectionResponseRepository.class);
    private final InspectionReviewRepository reviewRepository = mock(InspectionReviewRepository.class);
    private final AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
    private final InspectionResponseValidator validator = mock(InspectionResponseValidator.class);
    private final AuditEventService auditEventService = mock(AuditEventService.class);
    private final Authentication authentication = mock(Authentication.class);

    private final UUID technicianId = UUID.randomUUID();
    private final UUID inspectionId = UUID.randomUUID();
    private final UUID correctedItem = UUID.randomUUID();
    private final UUID lockedItem = UUID.randomUUID();

    private InspectionResponseService service;
    private Inspection inspection;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<InspectionEvidenceRepository> evidenceProvider = mock(ObjectProvider.class);
        ObjectProvider<InspectionReviewRepository> reviewProvider = mock(ObjectProvider.class);
        when(reviewProvider.getObject()).thenReturn(reviewRepository);
        service = new InspectionResponseService(
                inspectionRepository, responseRepository, userResolver, validator, new ObjectMapper(),
                evidenceProvider, reviewProvider, auditEventService
        );

        inspection = BeanUtils.instantiateClass(Inspection.class);
        ReflectionTestUtils.setField(inspection, "id", inspectionId);
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(inspection, "submittedAtServer", Instant.parse("2026-11-19T18:00:00Z"));
        ReflectionTestUtils.setField(inspection, "itemSnapshots", List.of(snapshot(correctedItem), snapshot(lockedItem)));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userResolver.requireUserId(authentication)).thenReturn(technicianId);
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        when(validator.validate(any(), any())).thenReturn(
                new InspectionResponseValue("novo valor", null, null, null, null, null)
        );
        when(responseRepository.saveAndFlush(any(InspectionResponse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectedInspectionMustBeReopenedBeforeAnyChange() {
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.REJECTED);
        UUID responseId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upsert(inspectionId, responseId, request(responseId, correctedItem, 0)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_REOPEN_REQUIRED");
    }

    @Test
    void itemOutsideItemsToCorrectIsRefusedByTheServer() {
        lastRejection(List.of(correctedItem));
        UUID responseId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upsert(inspectionId, responseId, request(responseId, lockedItem, 0)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_ITEM_NOT_IN_CORRECTION");
        verify(responseRepository, never()).saveAndFlush(any());
    }

    @Test
    void correctedResponseKeepsThePreviousValueInTheAudit() {
        lastRejection(List.of(correctedItem));
        InspectionResponse existing = InspectionResponse.create(
                UUID.randomUUID(), inspectionId, correctedItem, technicianId,
                new InspectionResponseValue("valor antigo", null, null, null, null, null),
                null, DEVICE_TIME, DEVICE_TIME, 0
        );
        when(responseRepository.findByIdAndInspectionId(existing.getId(), inspectionId))
                .thenReturn(Optional.of(existing));

        service.upsert(inspectionId, existing.getId(), request(existing.getId(), correctedItem, 0));

        verify(auditEventService).recordInspectionEvent(
                eq("RESPONSE_UPDATED"),
                eq("INSPECTION_RESPONSE"),
                eq(existing.getId()),
                eq(inspectionId),
                eq(DEVICE_TIME),
                argThat(previous -> previous.contains("\"valueText\":\"valor antigo\"")),
                argThat(current -> current.contains("\"valueText\":\"novo valor\""))
        );
    }

    @Test
    void rejectionWithoutItemsReopensTheWholeInspection() {
        lastRejection(List.of());
        UUID responseId = UUID.randomUUID();
        when(responseRepository.findByIdAndInspectionId(responseId, inspectionId)).thenReturn(Optional.empty());
        when(responseRepository.findByInspectionIdAndInspectionItemId(inspectionId, lockedItem))
                .thenReturn(Optional.empty());

        service.upsert(inspectionId, responseId, request(responseId, lockedItem, 0));

        verify(responseRepository).saveAndFlush(any(InspectionResponse.class));
        verify(auditEventService).recordInspectionEvent(
                eq("RESPONSE_CREATED"), eq("INSPECTION_RESPONSE"), eq(responseId), eq(inspectionId),
                eq(DEVICE_TIME), eq(null), argThat(current -> current.contains("\"valueText\":\"novo valor\""))
        );
    }

    @Test
    void firstExecutionDoesNotLookUpReviews() {
        ReflectionTestUtils.setField(inspection, "submittedAtServer", null);
        UUID responseId = UUID.randomUUID();
        when(responseRepository.findByIdAndInspectionId(responseId, inspectionId)).thenReturn(Optional.empty());
        when(responseRepository.findByInspectionIdAndInspectionItemId(inspectionId, lockedItem))
                .thenReturn(Optional.empty());

        service.upsert(inspectionId, responseId, request(responseId, lockedItem, 0));

        verifyNoInteractions(reviewRepository);
    }

    private void lastRejection(List<UUID> itemsToCorrect) {
        when(reviewRepository.findFirstByInspectionIdOrderByReviewCycleDesc(inspectionId)).thenReturn(Optional.of(
                InspectionReview.reject(inspectionId, UUID.randomUUID(), "Corrigir", null, DEVICE_TIME, 1, itemsToCorrect)
        ));
    }

    private SaveInspectionResponseRequest request(UUID responseId, UUID itemId, int baseVersion) {
        return new SaveInspectionResponseRequest(
                responseId, itemId, "novo valor", null, null, null, null, null, null, DEVICE_TIME, baseVersion
        );
    }

    private InspectionItemSnapshot snapshot(UUID id) {
        InspectionItemSnapshot snapshot = mock(InspectionItemSnapshot.class);
        when(snapshot.getId()).thenReturn(id);
        return snapshot;
    }
}
