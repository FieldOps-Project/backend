package com.fieldops.sync.application;

import tools.jackson.databind.ObjectMapper;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.sync.domain.model.SyncChange;
import com.fieldops.sync.domain.model.SyncCursor;
import com.fieldops.sync.infrastructure.persistence.SyncChangeReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncPullServiceTest {

    @Mock
    private SyncChangeReader changeReader;
    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    private SyncPullService service;
    private UUID technicianId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        service = new SyncPullService(changeReader, authenticatedUserResolver);
        technicianId = UUID.randomUUID();
        authentication = new TestingAuthenticationToken("technician", "password");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        lenient().when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(technicianId);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paginatesOneThousandChangesWithoutRepeatingOrSkippingTiedTimestamps() {
        Instant changedAt = Instant.parse("2026-08-01T14:15:00Z");
        List<SyncChange> allChanges = java.util.stream.IntStream.range(0, 1000)
                .mapToObj(index -> new SyncChange(
                        "INSPECTION", new UUID(0, index + 1), "UPSERT", index,
                        changedAt, new ObjectMapper().createObjectNode().put("index", index)
                ))
                .toList();
        when(changeReader.findChanges(eq(technicianId), any(), anyInt()))
                .thenAnswer(invocation -> {
                    SyncCursor cursor = invocation.getArgument(1);
                    int limit = invocation.getArgument(2);
                    return allChanges.stream()
                            .filter(change -> cursor == null
                                    || change.changedAt().isAfter(cursor.changedAt())
                                    || (change.changedAt().equals(cursor.changedAt())
                                    && change.entityId().compareTo(cursor.entityId()) > 0))
                            .sorted(Comparator.comparing(SyncChange::changedAt)
                                    .thenComparing(SyncChange::entityId))
                            .limit(limit)
                            .toList();
                });

        String cursor = null;
        java.util.Set<UUID> received = new java.util.HashSet<>();
        int pages = 0;
        boolean hasMore;
        do {
            var response = service.pull(cursor, 200);
            response.changes().forEach(change -> received.add(change.entityId()));
            assertThat(response.changes()).hasSize(200);
            cursor = response.nextCursor();
            hasMore = response.hasMore();
            pages++;
        } while (hasMore);

        assertThat(received).hasSize(1000);
        assertThat(pages).isEqualTo(5);
    }

    @Test
    void keepsTheCursorWhenThereAreNoChanges() {
        String cursor = new SyncCursor(Instant.now(), UUID.randomUUID()).encode();
        when(changeReader.findChanges(eq(technicianId), any(), anyInt())).thenReturn(List.of());

        var response = service.pull(cursor, 200);

        assertThat(response.changes()).isEmpty();
        assertThat(response.nextCursor()).isEqualTo(cursor);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void rejectsAnInvalidPageSizeBeforeReadingTheDatabase() {
        assertThatThrownBy(() -> service.pull(null, 1001))
                .hasFieldOrPropertyWithValue("code", "SYNC_LIMIT_INVALID");
    }
}
