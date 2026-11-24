package com.fieldops.inspection.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionReviewMigrationTest {

    @Test
    void reviewMigrationKeepsOneRecordPerCycleAndRequiresRejectionReason() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V19__create_inspection_reviews.sql"));

        assertThat(sql).contains("CREATE TABLE inspection_reviews");
        assertThat(sql).contains("CONSTRAINT ux_review_inspection_cycle UNIQUE (inspection_id, review_cycle)");
        assertThat(sql).contains("CHECK (decision IN ('APPROVED', 'REJECTED'))");
        assertThat(sql).contains("CHECK (decision <> 'REJECTED' OR (reason IS NOT NULL AND length(trim(reason)) > 0))");
        assertThat(sql).contains("REFERENCES inspection_item_snapshots(id)");
    }
}
