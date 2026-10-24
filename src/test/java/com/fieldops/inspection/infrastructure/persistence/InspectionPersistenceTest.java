package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.shared.infrastructure.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the Flyway migrations on a real PostgreSQL and checks the inspection
 * model end to end: mapping, optimistic locking and the constraints that the
 * database itself must enforce.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class InspectionPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private InspectionItemSnapshotRepository snapshotRepository;

    private UUID technicianId;
    private UUID supervisorId;
    private UUID clientId;
    private UUID otherClientId;
    private UUID siteId;
    private UUID otherSiteId;
    private UUID equipmentId;
    private UUID otherEquipmentId;
    private InspectionTemplateVersion published;
    private InspectionTemplateVersion draft;

    @BeforeEach
    void setUp() {
        technicianId = insertUser("TECHNICIAN");
        supervisorId = insertUser("SUPERVISOR");
        clientId = insertClient();
        otherClientId = insertClient();
        siteId = insertSite(clientId);
        otherSiteId = insertSite(otherClientId);
        equipmentId = insertEquipment(siteId);
        otherEquipmentId = insertEquipment(otherSiteId);

        InspectionTemplate template = InspectionTemplate.create(
                "Extintores " + UUID.randomUUID(), null, "Incendio", supervisorId
        );
        entityManager.persist(template);

        published = InspectionTemplateVersion.createPublished(template, 1, "Extintores", null, supervisorId);
        TemplateSection conditions = TemplateSection.create(published, "Condicoes fisicas", "Inspecao visual", 1);
        conditions.addItem(TemplateItem.create(
                conditions, "EXT-01", "O lacre esta intacto?", "Verifique o lacre do gatilho.",
                ResponseType.CONFORMITY, true, true, true, null, 1
        ));
        conditions.addItem(TemplateItem.create(
                conditions, "EXT-02", "Estado da pintura", null,
                ResponseType.SINGLE_CHOICE, false, false, false, "[\"Bom\",\"Regular\",\"Ruim\"]", 2
        ));
        published.addSection(conditions);
        TemplateSection signage = TemplateSection.create(published, "Sinalizacao", null, 2);
        signage.addItem(TemplateItem.create(
                signage, null, "Placa visivel?", null, ResponseType.BOOLEAN, true, false, false, null, 1
        ));
        published.addSection(signage);
        entityManager.persist(published);

        draft = InspectionTemplateVersion.createDraft(template, "Extintores", null);
        entityManager.persist(draft);
        entityManager.flush();
    }

    // ── Mapping and snapshot ────────────────────────────────────────────

    @Test
    void persistsInspectionWithFrozenItemSnapshots() {
        Inspection inspection = inspectionRepository.saveAndFlush(newInspection(null));
        UUID lacreItemId = published.getSections().get(0).getItems().get(0).getId();
        entityManager.clear();

        Inspection loaded = inspectionRepository.findById(inspection.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(InspectionStatus.ASSIGNED);
        assertThat(loaded.getPriority()).isEqualTo(InspectionPriority.MEDIUM);
        assertThat(loaded.getEquipmentId()).isNull();
        assertThat(loaded.getTemplateVersionId()).isEqualTo(published.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isZero();

        List<InspectionItemSnapshot> snapshots =
                snapshotRepository.findByInspectionIdOrderBySectionOrderAscItemOrderAsc(inspection.getId());
        assertThat(snapshots).extracting(InspectionItemSnapshot::getItemTitle)
                .containsExactly("O lacre esta intacto?", "Estado da pintura", "Placa visivel?");
        InspectionItemSnapshot lacre = snapshots.get(0);
        assertThat(lacre.getSourceTemplateItemId()).isEqualTo(lacreItemId);
        assertThat(lacre.getSectionTitle()).isEqualTo("Condicoes fisicas");
        assertThat(lacre.getSectionDescription()).isEqualTo("Inspecao visual");
        assertThat(lacre.getItemCode()).isEqualTo("EXT-01");
        assertThat(lacre.getItemDescription()).isEqualTo("Verifique o lacre do gatilho.");
        assertThat(lacre.getResponseType()).isEqualTo(ResponseType.CONFORMITY);
        assertThat(lacre.isRequired()).isTrue();
        assertThat(lacre.getRulesJson()).contains("\"evidenceRequiredOnFailure\": true");
        assertThat(lacre.getOptionsJson()).isNull();
        assertThat(snapshots.get(1).getOptionsJson()).isEqualTo("[\"Bom\", \"Regular\", \"Ruim\"]");
        assertThat(snapshots.get(2).getSectionOrder()).isEqualTo(2);

        jdbc.update("UPDATE template_items SET title = 'Texto reescrito' WHERE id = ?", lacreItemId);
        entityManager.clear();

        assertThat(snapshotRepository.findById(lacre.getId()).orElseThrow().getItemTitle())
                .isEqualTo("O lacre esta intacto?");
    }

    @Test
    void versionIncrementsOnEveryUpdate() {
        Inspection inspection = inspectionRepository.saveAndFlush(newInspection(equipmentId));
        assertThat(inspection.getVersion()).isZero();

        ReflectionTestUtils.setField(inspection, "instructions", "Levar escada");
        inspectionRepository.saveAndFlush(inspection);
        assertThat(inspection.getVersion()).isEqualTo(1);

        ReflectionTestUtils.setField(inspection, "priority", InspectionPriority.HIGH);
        inspectionRepository.saveAndFlush(inspection);
        assertThat(inspection.getVersion()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT version FROM inspections WHERE id = ?", Integer.class, inspection.getId()))
                .isEqualTo(2);
    }

    // ── Database constraints ────────────────────────────────────────────

    @Test
    void statusConstraintAcceptsExactlyTheEightBusinessStates() {
        String definition = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c
                WHERE c.conrelid = 'inspections'::regclass AND c.contype = 'c'
                  AND pg_get_constraintdef(c.oid) LIKE '%status%IN_PROGRESS%'
                """, String.class);
        Matcher values = Pattern.compile("'([A-Z_]+)'").matcher(definition);
        List<String> accepted = values.results().map(m -> m.group(1)).toList();

        assertThat(accepted).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(InspectionStatus.values()).map(Enum::name).toList()
        );
        assertThat(accepted).hasSize(8);
        assertRejected(Map.of("status", "PAUSED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"client_id", "site_id", "technician_id", "template_version_id", "scheduled_for"})
    void requiredColumnsCannotBeNull(String column) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put(column, null);

        assertRejected(overrides);
    }

    @Test
    void equipmentIsOptional() {
        insertInspection(Map.of());
        insertInspection(Map.of("equipment_id", equipmentId));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM inspections", Integer.class)).isEqualTo(2);
    }

    @Test
    void siteMustBelongToTheClient() {
        assertRejected(Map.of("site_id", otherSiteId));
    }

    @Test
    void equipmentMustBelongToTheSite() {
        assertRejected(Map.of("equipment_id", otherEquipmentId));
    }

    @Test
    void draftTemplateVersionIsRejected() {
        assertRejected(Map.of("template_version_id", draft.getId()));
    }

    @Test
    void cancellationWithoutReasonIsRejected() {
        assertRejected(Map.of(
                "status", "CANCELED", "canceled_at", Timestamp.from(Instant.now()), "canceled_by", supervisorId
        ));
    }

    @Test
    void cancellationWithDateAuthorAndReasonIsAccepted() {
        insertInspection(Map.of(
                "status", "CANCELED", "canceled_at", Timestamp.from(Instant.now()), "canceled_by", supervisorId,
                "canceled_reason", "Cliente fechado"
        ));
    }

    @Test
    void serverReceiptWithoutDeviceTimestampIsRejected() {
        assertRejected(Map.of(
                "status", "IN_PROGRESS", "started_at_server", Timestamp.from(Instant.parse("2026-10-23T16:00:00Z"))
        ));
    }

    @Test
    void offlineExecutionKeepsDeviceAndServerTimes() {
        Timestamp device = Timestamp.from(Instant.parse("2026-10-23T10:00:00Z"));
        Timestamp server = Timestamp.from(Instant.parse("2026-10-23T16:00:00Z"));

        UUID id = insertInspection(Map.of(
                "status", "SUBMITTED",
                "started_at_device", device, "started_at_server", server,
                "completed_at_device", device, "submitted_at_server", server
        ));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT started_at_device, started_at_server FROM inspections WHERE id = ?", id
        );
        assertThat(row.get("started_at_device")).isNotEqualTo(row.get("started_at_server"));
    }

    @Test
    void snapshotsCannotBeUpdated() {
        Inspection inspection = inspectionRepository.saveAndFlush(newInspection(null));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE inspection_item_snapshots SET item_title = 'Alterado' WHERE inspection_id = ?",
                inspection.getId()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void recommendedIndexesExist() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename IN ('inspections', 'inspection_item_snapshots')",
                String.class
        );

        assertThat(indexes).contains(
                "ix_insp_tech_status", "ix_insp_sup_status", "ix_insp_client", "ix_insp_site",
                "ix_insp_equipment", "ix_insp_scheduled", "ix_insp_status_sched",
                "ix_snap_inspection", "ix_snap_order"
        );
    }

    @Test
    void deviceAndServerTimestampsAreDistinctColumns() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'inspections'",
                String.class
        );

        assertThat(columns).contains(
                "started_at_device", "started_at_server", "completed_at_device", "submitted_at_server"
        );
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private Inspection newInspection(UUID equipment) {
        return Inspection.create(
                published, clientId, siteId, equipment, technicianId, supervisorId, supervisorId,
                "Extintores - Bloco A", null, null, Instant.now().plus(1, ChronoUnit.DAYS)
        );
    }

    private void assertRejected(Map<String, Object> overrides) {
        assertThatThrownBy(() -> insertInspection(overrides))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertInspection(Map<String, Object> overrides) {
        UUID id = UUID.randomUUID();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("template_version_id", published.getId());
        values.put("client_id", clientId);
        values.put("site_id", siteId);
        values.put("technician_id", technicianId);
        values.put("supervisor_id", supervisorId);
        values.put("created_by", supervisorId);
        values.put("title", "Inspecao");
        values.put("status", "ASSIGNED");
        values.put("scheduled_for", Timestamp.from(Instant.now()));
        values.putAll(overrides);

        String columns = String.join(", ", values.keySet());
        String placeholders = values.keySet().stream().map(key -> "?").collect(Collectors.joining(", "));
        jdbc.update("INSERT INTO inspections (" + columns + ") VALUES (" + placeholders + ")", values.values().toArray());
        return id;
    }

    private UUID insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                id, role, id + "@example.com", "$2a$10$hash", role
        );
        return id;
    }

    private UUID insertClient() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clients (id, name) VALUES (?, ?)", id, "Cliente " + id);
        return id;
    }

    private UUID insertSite(UUID client) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO inspection_sites (id, client_id, name) VALUES (?, ?, ?)", id, client, "Local");
        return id;
    }

    private UUID insertEquipment(UUID site) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO equipment (id, site_id, name, qr_code) VALUES (?, ?, ?, ?)",
                id, site, "Extintor", "QR-" + id
        );
        return id;
    }
}
