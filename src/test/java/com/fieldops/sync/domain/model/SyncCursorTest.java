package com.fieldops.sync.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncCursorTest {

    @Test
    void roundTripsTheTimestampAndIdWithoutExposingTheCursorFormat() {
        SyncCursor cursor = new SyncCursor(
                Instant.parse("2026-08-01T14:15:00.123Z"),
                UUID.fromString("62327a9a-ec19-46a2-89ae-b49086b657f9")
        );

        String encoded = cursor.encode();

        assertThat(encoded).doesNotContain("2026-08-01");
        assertThat(SyncCursor.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedCursors() {
        assertThatThrownBy(() -> SyncCursor.decode("not-a-cursor"))
                .hasFieldOrPropertyWithValue("code", "SYNC_CURSOR_INVALID");
    }
}
