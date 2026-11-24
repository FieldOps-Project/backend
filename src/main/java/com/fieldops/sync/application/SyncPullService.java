package com.fieldops.sync.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.sync.domain.model.SyncChange;
import com.fieldops.sync.domain.model.SyncCursor;
import com.fieldops.sync.infrastructure.persistence.SyncChangeReader;
import com.fieldops.sync.presentation.dto.SyncChangeResponse;
import com.fieldops.sync.presentation.dto.SyncPullResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SyncPullService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    private final SyncChangeReader changeReader;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final Clock clock;

    public SyncPullService(
            SyncChangeReader changeReader,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.changeReader = changeReader;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public SyncPullResponse pull(String encodedCursor, Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException(
                    "SYNC_LIMIT_INVALID",
                    "The synchronization limit must be between 1 and " + MAX_LIMIT
            );
        }

        UUID technicianId = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );
        SyncCursor cursor = SyncCursor.decode(encodedCursor);
        List<SyncChange> fetched = changeReader.findChanges(technicianId, cursor, limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<SyncChange> page = hasMore ? fetched.subList(0, limit) : fetched;
        String nextCursor = page.isEmpty()
                ? encodedCursor
                : page.get(page.size() - 1).cursor().encode();

        return new SyncPullResponse(
                page.stream().map(SyncChangeResponse::from).toList(),
                nextCursor,
                hasMore,
                Instant.now(clock)
        );
    }
}
