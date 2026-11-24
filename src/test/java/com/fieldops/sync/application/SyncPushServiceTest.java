package com.fieldops.sync.application;

import tools.jackson.databind.ObjectMapper;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.shared.domain.exception.PayloadTooLargeException;
import com.fieldops.sync.domain.model.SyncOperationResultStatus;
import com.fieldops.sync.presentation.dto.SyncOperationRequest;
import com.fieldops.sync.presentation.dto.SyncOperationResult;
import com.fieldops.sync.presentation.dto.SyncPushRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncPushServiceTest {

    @Mock
    private SyncOperationProcessor operationProcessor;
    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    private SyncPushService service;
    private UUID deviceId;
    private UUID userId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        service = new SyncPushService(operationProcessor, authenticatedUserResolver, 100);
        deviceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        authentication = new TestingAuthenticationToken("technician", "password");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        lenient().when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(userId);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processesOperationsInDependencyOrderAndKeepsResponseOrder() {
        UUID responseOperationId = UUID.randomUUID();
        UUID evidenceOperationId = UUID.randomUUID();
        SyncOperationRequest response = operation(responseOperationId, List.of(), "INSPECTION_RESPONSE");
        SyncOperationRequest evidence = operation(evidenceOperationId, List.of(responseOperationId), "INSPECTION_EVIDENCE");
        when(operationProcessor.process(eq(deviceId), eq(userId), any(SyncOperationRequest.class)))
                .thenAnswer(invocation -> {
                    SyncOperationRequest operation = invocation.getArgument(2);
                    return applied(operation.operationId());
                });

        var result = service.push(new SyncPushRequest(deviceId, "cursor", List.of(evidence, response)));

        assertThat(result.results()).extracting(SyncOperationResult::operationId)
                .containsExactly(evidenceOperationId, responseOperationId);
        assertThat(result.results()).extracting(SyncOperationResult::status)
                .containsOnly(SyncOperationResultStatus.APPLIED);
        assertThat(result.nextCursor()).isNotBlank();
        assertThat(result.serverTime()).isNotNull();
        InOrder order = inOrder(operationProcessor);
        order.verify(operationProcessor).process(deviceId, userId, response);
        order.verify(operationProcessor).process(deviceId, userId, evidence);
    }

    @Test
    void propagatesARejectedDependencyWithoutTryingTheDependentOperation() {
        UUID responseOperationId = UUID.randomUUID();
        UUID evidenceOperationId = UUID.randomUUID();
        SyncOperationRequest response = operation(responseOperationId, List.of(), "INSPECTION_RESPONSE");
        SyncOperationRequest evidence = operation(evidenceOperationId, List.of(responseOperationId), "INSPECTION_EVIDENCE");
        when(operationProcessor.process(deviceId, userId, response))
                .thenReturn(new SyncOperationResult(responseOperationId, SyncOperationResultStatus.REJECTED,
                        null, "INVALID", "invalid response"));
        when(operationProcessor.recordSkipped(eq(deviceId), eq(userId), eq(evidence),
                eq(SyncOperationResultStatus.DEPENDENCY_FAILED), eq("SYNC_DEPENDENCY_FAILED"), any(String.class)))
                .thenReturn(new SyncOperationResult(evidenceOperationId, SyncOperationResultStatus.DEPENDENCY_FAILED,
                        null, "SYNC_DEPENDENCY_FAILED", "A dependency operation was not applied"));

        var result = service.push(new SyncPushRequest(deviceId, null, List.of(response, evidence)));

        assertThat(result.results().get(0).status()).isEqualTo(SyncOperationResultStatus.REJECTED);
        assertThat(result.results().get(1).status()).isEqualTo(SyncOperationResultStatus.DEPENDENCY_FAILED);
        verify(operationProcessor, never()).process(deviceId, userId, evidence);
    }

    @Test
    void rejectsABatchAboveTheConfiguredLimitBeforeProcessing() {
        service = new SyncPushService(operationProcessor, authenticatedUserResolver, 1);
        SyncOperationRequest first = operation(UUID.randomUUID(), List.of(), "INSPECTION_RESPONSE");
        SyncOperationRequest second = operation(UUID.randomUUID(), List.of(), "INSPECTION_RESPONSE");

        assertThatThrownBy(() -> service.push(new SyncPushRequest(deviceId, null, List.of(first, second))))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasFieldOrPropertyWithValue("code", "SYNC_BATCH_TOO_LARGE");
        verify(operationProcessor, never()).process(any(), any(), any());
    }

    private SyncOperationRequest operation(UUID id, List<UUID> dependencies, String entityType) {
        return new SyncOperationRequest(
                id, entityType, UUID.randomUUID(),
                "INSPECTION_EVIDENCE".equals(entityType) ? "UPLOAD" : "UPSERT",
                0, dependencies, new ObjectMapper().createObjectNode()
        );
    }

    private SyncOperationResult applied(UUID operationId) {
        return new SyncOperationResult(operationId, SyncOperationResultStatus.APPLIED, 0, null, null);
    }
}
