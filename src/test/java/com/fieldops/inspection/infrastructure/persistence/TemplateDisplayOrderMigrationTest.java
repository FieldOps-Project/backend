package com.fieldops.inspection.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateDisplayOrderMigrationTest {

    private static final String SCRIPT = "db/migration/V10__defer_template_display_order_constraints.sql";

    private String script() throws Exception {
        ClassPathResource resource = new ClassPathResource(SCRIPT);
        assertThat(resource.exists()).isTrue();
        return new String(resource.getInputStream().readAllBytes()).replaceAll("\\s+", " ");
    }

    @Test
    void sectionOrderUniquenessIsCheckedAtCommit() throws Exception {
        String content = script();

        assertThat(content).contains("DROP INDEX ux_section_order;");
        assertThat(content).contains(
                "ADD CONSTRAINT ux_section_order UNIQUE (template_version_id, display_order) DEFERRABLE INITIALLY DEFERRED"
        );
        assertThat(content).contains("CHECK (display_order >= 1)");
    }

    @Test
    void itemOrderUniquenessIsCheckedAtCommit() throws Exception {
        String content = script();

        assertThat(content).contains("DROP INDEX ux_item_order;");
        assertThat(content).contains(
                "ADD CONSTRAINT ux_item_order UNIQUE (section_id, display_order) DEFERRABLE INITIALLY DEFERRED"
        );
        assertThat(content).contains("ck_item_order_positive");
    }
}
