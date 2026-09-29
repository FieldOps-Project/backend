package com.fieldops.inspection.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionTemplateMigrationTest {

    @Test
    void migrationScriptV9ExistsAndContainsAllTableDefinitions() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V9__create_inspection_templates.sql");
        assertThat(resource.exists()).isTrue();

        String content = new String(resource.getInputStream().readAllBytes());

        // inspection_templates table
        assertThat(content).contains("CREATE TABLE inspection_templates");
        assertThat(content).contains("category");
        assertThat(content).contains("CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))");
        assertThat(content).contains("fk_template_created_by");

        // inspection_template_versions table
        assertThat(content).contains("CREATE TABLE inspection_template_versions");
        assertThat(content).contains("ux_tpl_version");
        assertThat(content).contains("fk_version_template");

        // template_sections table
        assertThat(content).contains("CREATE TABLE template_sections");
        assertThat(content).contains("ux_section_order");
        assertThat(content).contains("fk_section_version");

        // template_items table
        assertThat(content).contains("CREATE TABLE template_items");
        assertThat(content).contains("ux_item_order");
        assertThat(content).contains("fk_item_section");
        assertThat(content).contains("'TEXT_SHORT', 'TEXT_LONG', 'NUMBER', 'BOOLEAN'");
        assertThat(content).contains("'CONFORMITY', 'SINGLE_CHOICE', 'DATE'");
    }

    @Test
    void migrationContainsDisplayOrderUniqueConstraints() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V9__create_inspection_templates.sql");
        String content = new String(resource.getInputStream().readAllBytes());

        // display_order unique within version (sections)
        assertThat(content).contains("UNIQUE INDEX ux_section_order ON template_sections (template_version_id, display_order)");

        // display_order unique within section (items)
        assertThat(content).contains("UNIQUE INDEX ux_item_order ON template_items (section_id, display_order)");
    }

    @Test
    void migrationContainsVersionNumberUniqueConstraint() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V9__create_inspection_templates.sql");
        String content = new String(resource.getInputStream().readAllBytes());

        assertThat(content).contains("UNIQUE INDEX ux_tpl_version ON inspection_template_versions (template_id, version_number)");
    }
}
