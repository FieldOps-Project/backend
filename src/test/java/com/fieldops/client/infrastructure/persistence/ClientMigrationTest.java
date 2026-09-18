package com.fieldops.client.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClientMigrationTest {

    @Test
    void clientsMigrationCreatesRequiredColumnsConstraintsAndIndexes() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V5__create_clients.sql"));

        assertThat(sql).contains("CREATE TABLE clients");
        assertThat(sql).contains("name VARCHAR(255) NOT NULL");
        assertThat(sql).contains("legal_name VARCHAR(255)");
        assertThat(sql).contains("document VARCHAR(14)");
        assertThat(sql).contains("status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'");
        assertThat(sql).contains("created_at TIMESTAMPTZ NOT NULL DEFAULT now()");
        assertThat(sql).contains("updated_at TIMESTAMPTZ NOT NULL DEFAULT now()");
        assertThat(sql).contains("version INTEGER NOT NULL DEFAULT 0");
        assertThat(sql).contains("CONSTRAINT ck_clients_document_format");
        assertThat(sql).contains("CREATE INDEX ix_clients_status ON clients (status);");
        assertThat(sql).contains("CREATE INDEX ix_clients_name ON clients (lower(name));");
        assertThat(sql).contains("CREATE INDEX ix_clients_legal_name ON clients (lower(legal_name));");
        assertThat(sql).doesNotContain("UNIQUE");
    }
}
