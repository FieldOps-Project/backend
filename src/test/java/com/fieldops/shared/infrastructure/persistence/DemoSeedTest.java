package com.fieldops.shared.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the migrations and the demo seed of the {@code demo} profile on a
 * real PostgreSQL and checks the scenario the demonstration depends on.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("demo")
class DemoSeedTest {

    private static final String DEMO_PASSWORD = "Demo@1234";
    private static final List<String> SEEDED_TABLES = List.of(
            "users", "clients", "inspection_sites", "equipment", "inspection_templates",
            "inspection_template_versions", "template_sections", "template_items", "inspections",
            "inspection_item_snapshots", "inspection_responses", "non_conformities",
            "inspection_reviews", "inspection_review_items"
    );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void demoUsersOfEachProfileAuthenticateWithTheDocumentedPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Map<String, String> expectedRoles = Map.of(
                "admin@fieldops.local", "ADMIN",
                "supervisor@fieldops.local", "SUPERVISOR",
                "tecnico@fieldops.local", "TECHNICIAN"
        );

        expectedRoles.forEach((email, role) -> {
            Map<String, Object> user = jdbc.queryForMap(
                    "SELECT role, status, password_hash FROM users WHERE email = ?", email
            );
            assertThat(user.get("role")).isEqualTo(role);
            assertThat(user.get("status")).isEqualTo("ACTIVE");
            assertThat(encoder.matches(DEMO_PASSWORD, (String) user.get("password_hash"))).isTrue();
        });
    }

    @Test
    void publishedTemplateCoversTheSevenResponseTypes() {
        List<String> types = jdbc.queryForList("""
                SELECT DISTINCT ti.response_type
                FROM template_items ti
                JOIN template_sections ts ON ts.id = ti.section_id
                JOIN inspection_template_versions v ON v.id = ts.template_version_id
                WHERE v.published_at IS NOT NULL AND v.active_for_new_inspections
                """, String.class);

        assertThat(types).containsExactlyInAnyOrder(
                "TEXT_SHORT", "TEXT_LONG", "NUMBER", "BOOLEAN", "CONFORMITY", "SINGLE_CHOICE", "DATE"
        );
    }

    @Test
    void scenarioHasOneInspectionPerDemoStateAndTheGeneratorQrCode() {
        List<String> statuses = jdbc.queryForList("SELECT status FROM inspections", String.class);
        assertThat(statuses).containsExactlyInAnyOrder("ASSIGNED", "IN_PROGRESS", "SUBMITTED", "APPROVED", "REJECTED");

        assertThat(jdbc.queryForObject(
                "SELECT name FROM equipment WHERE qr_code = 'FIELDOPS-GD-001'", String.class
        )).isEqualTo("Gerador GD-001");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM non_conformities n JOIN inspections i ON i.id = n.inspection_id
                WHERE i.status = 'APPROVED' AND n.status = 'OPEN'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM inspection_reviews r
                JOIN inspection_review_items ri ON ri.review_id = r.id
                JOIN inspections i ON i.id = r.inspection_id
                WHERE i.status = 'REJECTED' AND r.decision = 'REJECTED' AND r.reason IS NOT NULL
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void runningTheSeedAgainDoesNotDuplicateRecords() throws Exception {
        Map<String, Integer> before = countSeededTables();

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/demo/R__demo_seed.sql"));
        }

        assertThat(countSeededTables()).isEqualTo(before);
    }

    private Map<String, Integer> countSeededTables() {
        return SEEDED_TABLES.stream().collect(Collectors.toMap(
                table -> table,
                table -> jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)
        ));
    }
}
