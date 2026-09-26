package com.fieldops.client.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionSiteMigrationTest {

    @Test
    void inspectionSitesMigrationCreatesRelationshipConstraintsAndIndexes() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V6__create_inspection_sites.sql"));

        assertThat(sql).contains("CREATE TABLE inspection_sites");
        assertThat(sql).contains("client_id UUID NOT NULL");
        assertThat(sql).contains("name VARCHAR(255) NOT NULL");
        assertThat(sql).contains("description VARCHAR(1000)");
        assertThat(sql).contains("latitude NUMERIC(9, 6)");
        assertThat(sql).contains("longitude NUMERIC(9, 6)");
        assertThat(sql).contains("status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'");
        assertThat(sql).contains("created_at TIMESTAMPTZ NOT NULL DEFAULT now()");
        assertThat(sql).contains("updated_at TIMESTAMPTZ NOT NULL DEFAULT now()");
        assertThat(sql).contains("version INTEGER NOT NULL DEFAULT 0");
        assertThat(sql).contains("CONSTRAINT fk_inspection_sites_client FOREIGN KEY (client_id) REFERENCES clients(id)");
        assertThat(sql).contains("CONSTRAINT ck_inspection_sites_coordinates");
        assertThat(sql).contains("CREATE INDEX ix_sites_client ON inspection_sites (client_id);");
        assertThat(sql).contains("CREATE INDEX ix_sites_status ON inspection_sites (status);");
    }
}
