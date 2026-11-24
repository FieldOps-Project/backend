package com.fieldops.inspection.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceFileSyncMigrationTest {

    @Test
    void evidenceFileCanBePendingAfterMetadataIsApplied() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V21__allow_pending_evidence_files.sql"));

        assertThat(sql).contains("ALTER COLUMN uploaded_at DROP NOT NULL");
    }
}
