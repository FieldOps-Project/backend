package com.fieldops.audit.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMigrationTest {

    @Test
    void auditMigrationStoresActorAndNonSensitiveChanges() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V5__create_audit_events.sql"));

        assertThat(sql).contains("CREATE TABLE audit_events");
        assertThat(sql).contains("actor_user_id UUID NOT NULL REFERENCES users(id)");
        assertThat(sql).contains("action VARCHAR(64) NOT NULL");
        assertThat(sql).contains("before_values TEXT");
        assertThat(sql).contains("after_values TEXT");
        assertThat(sql).doesNotContain("password_hash");
    }
}
