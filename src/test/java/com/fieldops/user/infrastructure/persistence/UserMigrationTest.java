package com.fieldops.user.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UserMigrationTest {

    @Test
    void usersMigrationCreatesRequiredColumnsAndIndexes() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V2__create_users.sql"));

        assertThat(sql).contains("CREATE TABLE users");
        assertThat(sql).contains("password_hash VARCHAR(255) NOT NULL");
        assertThat(sql).contains("role VARCHAR(32) NOT NULL");
        assertThat(sql).contains("status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'");
        assertThat(sql).contains("created_at TIMESTAMPTZ NOT NULL DEFAULT now()");
        assertThat(sql).contains("updated_at TIMESTAMPTZ NOT NULL DEFAULT now()");
        assertThat(sql).contains("version INTEGER NOT NULL DEFAULT 0");
        assertThat(sql).contains("CREATE UNIQUE INDEX ux_users_email ON users (lower(email));");
        assertThat(sql).contains("CREATE INDEX ix_users_status ON users (status);");
        assertThat(sql).contains("CREATE INDEX ix_users_role ON users (role);");
    }
}
