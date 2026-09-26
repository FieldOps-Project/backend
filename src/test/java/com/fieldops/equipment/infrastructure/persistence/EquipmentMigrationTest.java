package com.fieldops.equipment.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentMigrationTest {

    @Test
    void migrationScriptV7ExistsAndContainsTableDefinition() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V7__create_equipment.sql");
        assertThat(resource.exists()).isTrue();

        String content = new String(resource.getInputStream().readAllBytes());
        assertThat(content).contains("CREATE TABLE equipment");
        assertThat(content).contains("site_id UUID NOT NULL");
        assertThat(content).contains("qr_code VARCHAR(255) NOT NULL UNIQUE");
        assertThat(content).contains("fk_equipment_site");
        assertThat(content).contains("ix_equipment_qr");
    }
}
