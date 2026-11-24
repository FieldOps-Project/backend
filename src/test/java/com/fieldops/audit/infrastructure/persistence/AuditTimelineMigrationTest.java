package com.fieldops.audit.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuditTimelineMigrationTest {

    @Test
    void timelineMigrationAddsRecommendedIndexesAndBlocksChanges() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V20__extend_audit_events.sql"));

        assertThat(sql).contains("ADD COLUMN inspection_id", "ADD COLUMN device_occurred_at",
                "ADD COLUMN request_id", "ADD COLUMN device_id");
        assertThat(sql).contains("CREATE INDEX ix_audit_insp ON audit_events (inspection_id)");
        assertThat(sql).contains("CREATE INDEX ix_audit_occurred ON audit_events (occurred_at)");
        assertThat(sql).contains("CREATE INDEX ix_audit_request ON audit_events (request_id)");
        assertThat(sql).contains("BEFORE UPDATE OR DELETE ON audit_events");
        assertThat(sql).contains("BEFORE TRUNCATE ON audit_events");
    }
}
