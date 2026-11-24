package com.fieldops.sync.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.shared.domain.exception.PayloadTooLargeException;
import com.fieldops.sync.domain.model.SyncOperationResultStatus;
import com.fieldops.sync.presentation.dto.SyncOperationRequest;
import com.fieldops.sync.presentation.dto.SyncOperationResult;
import com.fieldops.sync.presentation.dto.SyncPushRequest;
import com.fieldops.sync.presentation.dto.SyncPushResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SyncPushService {

    private final SyncOperationProcessor operationProcessor;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final int maxOperations;
    private final Clock clock;

    public SyncPushService(
            SyncOperationProcessor operationProcessor,
            AuthenticatedUserResolver authenticatedUserResolver,
            @Value("${app.sync.max-operations:100}") int maxOperations
    ) {
        this.operationProcessor = operationProcessor;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.maxOperations = maxOperations;
        this.clock = Clock.systemUTC();
    }

    public SyncPushResponse push(SyncPushRequest request) {
        if (request.operations().size() > maxOperations) {
            throw new PayloadTooLargeException(
                    "SYNC_BATCH_TOO_LARGE",
                    "The synchronization batch exceeds the configured operation limit of " + maxOperations
            );
        }

        UUID userId = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );
        Map<UUID, SyncOperationRequest> operationsById = new HashMap<>();
        Map<UUID, Integer> operationCounts = new HashMap<>();
        request.operations().forEach(operation -> {
            operationsById.putIfAbsent(operation.operationId(), operation);
            operationCounts.merge(operation.operationId(), 1, Integer::sum);
        });

        Map<UUID, SyncOperationResult> resultById = new LinkedHashMap<>();
        boolean progress;
        do {
            progress = false;
            for (SyncOperationRequest operation : request.operations()) {
                UUID operationId = operation.operationId();
                if (resultById.containsKey(operationId)) {
                    continue;
                }
                if (operationCounts.get(operationId) > 1) {
                    resultById.put(operationId, operationProcessor.recordSkipped(
                            request.deviceId(), userId, operation, SyncOperationResultStatus.REJECTED,
                            "SYNC_DUPLICATE_OPERATION_ID", "The batch contains the operation id more than once"
                    ));
                    progress = true;
                    continue;
                }

                List<UUID> dependencies = operation.dependencies();
                UUID missingDependency = dependencies.stream()
                        .filter(dependency -> dependency == null || !operationsById.containsKey(dependency))
                        .findFirst()
                        .orElse(null);
                if (missingDependency != null || dependencies.stream().anyMatch(dependency -> dependency == null)) {
                    resultById.put(operationId, operationProcessor.recordSkipped(
                            request.deviceId(), userId, operation, SyncOperationResultStatus.REJECTED,
                            "SYNC_DEPENDENCY_NOT_FOUND", "The operation depends on an operation outside this batch"
                    ));
                    progress = true;
                    continue;
                }

                if (dependencies.stream().anyMatch(dependency -> resultById.containsKey(dependency)
                        && !isSuccessful(resultById.get(dependency).status()))) {
                    resultById.put(operationId, operationProcessor.recordSkipped(
                            request.deviceId(), userId, operation, SyncOperationResultStatus.DEPENDENCY_FAILED,
                            "SYNC_DEPENDENCY_FAILED", "A dependency operation was not applied"
                    ));
                    progress = true;
                    continue;
                }
                if (dependencies.stream().anyMatch(dependency -> !resultById.containsKey(dependency))) {
                    continue;
                }

                resultById.put(operationId, operationProcessor.process(request.deviceId(), userId, operation));
                progress = true;
            }
        } while (progress && resultById.size() < operationsById.size());

        for (SyncOperationRequest operation : request.operations()) {
            if (!resultById.containsKey(operation.operationId())) {
                resultById.put(operation.operationId(), operationProcessor.recordSkipped(
                        request.deviceId(), userId, operation, SyncOperationResultStatus.REJECTED,
                        "SYNC_DEPENDENCY_CYCLE", "The operation dependencies contain a cycle"
                ));
            }
        }

        Instant serverTime = clock.instant();
        List<SyncOperationResult> results = request.operations().stream()
                .map(operation -> resultById.get(operation.operationId()))
                .toList();
        return new SyncPushResponse(results, List.of(), UUID.randomUUID().toString(), serverTime);
    }

    private boolean isSuccessful(com.fieldops.sync.domain.model.SyncOperationResultStatus status) {
        return status == SyncOperationResultStatus.APPLIED || status == SyncOperationResultStatus.ALREADY_APPLIED;
    }
}
