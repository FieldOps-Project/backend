package com.fieldops.inspection.infrastructure.persistence;

import tools.jackson.databind.ObjectMapper;
import com.fieldops.inspection.domain.model.FieldPosition;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.inspection.domain.model.InspectionResponseConformity;
import com.fieldops.inspection.domain.model.InspectionResponseValue;
import com.fieldops.inspection.domain.model.InspectionEvidence;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.NonConformity;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.inspection.application.InspectionListQuery;
import com.fieldops.shared.infrastructure.config.JpaAuditingConfig;
import com.fieldops.sync.domain.model.SyncChange;
import com.fieldops.sync.infrastructure.persistence.SyncChangeReader;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Autowired
    private InspectionResponseRepository responseRepository;

    @Autowired
    private InspectionEvidenceRepository evidenceRepository;

    @Autowired
    private NonConformityRepository nonConformityRepository;

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
    void mobileInspectionLookupRequiresTheAuthenticatedTechnician() {
        Inspection inspection = inspectionRepository.saveAndFlush(newInspection(null));
        UUID anotherTechnicianId = insertUser("TECHNICIAN");

        assertThat(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(
                inspection.getId(), technicianId
        )).isPresent();
        assertThat(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(
                inspection.getId(), anotherTechnicianId
        )).isEmpty();
    }

    @Test
    void syncPullReaderReturnsOnlyTheTechniciansInspectionGraph() {
        Inspection inspection = inspectionRepository.saveAndFlush(newInspection(equipmentId));
        SyncChangeReader reader = new SyncChangeReader(jdbc, new ObjectMapper());

        List<SyncChange> changes = reader.findChanges(technicianId, null, 100);

        assertThat(changes).extracting(SyncChange::entityType)
                .contains("INSPECTION", "INSPECTION_SNAPSHOT", "CLIENT", "INSPECTION_SITE", "EQUIPMENT");
        assertThat(changes).extracting(SyncChange::entityId)
                .contains(inspection.getId(), clientId, siteId, equipmentId)
                .doesNotContain(otherClientId, otherSiteId, otherEquipmentId);
        assertThat(changes).allMatch(change -> change.payload().isObject());
    }

    @Test
    void persistsTypedResponseAndPreventsTwoCurrentResponsesForOneItem() {
        Inspection inspection = inspectionRepository.saveAndFlush(newInspection(null));
        InspectionItemSnapshot snapshot = snapshotRepository.findByInspectionIdOrderBySectionOrderAscItemOrderAsc(
                inspection.getId()
        ).get(0);
        UUID responseId = UUID.randomUUID();

        InspectionResponse saved = responseRepository.saveAndFlush(InspectionResponse.create(
                responseId,
                inspection.getId(),
                snapshot.getId(),
                technicianId,
                new InspectionResponseValue(null, null, null, null, null,
                        InspectionResponseConformity.CONFORMING),
                null,
                Instant.parse("2026-08-01T13:12:30Z"),
                Instant.parse("2026-08-01T16:12:30Z"),
                0
        ));

        assertThat(responseRepository.findById(responseId).orElseThrow().getConformity())
                .isEqualTo(InspectionResponseConformity.CONFORMING);
        assertThat(saved.getAnsweredAtServer()).isEqualTo(Instant.parse("2026-08-01T16:12:30Z"));

        assertThatThrownBy(() -> responseRepository.saveAndFlush(InspectionResponse.create(
                UUID.randomUUID(),
                inspection.getId(),
                snapshot.getId(),
                technicianId,
                new InspectionResponseValue("duplicada", null, null, null, null, null),
                null,
                Instant.parse("2026-08-01T13:13:30Z"),
                Instant.parse("2026-08-01T16:13:30Z"),
                0
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsEvidenceMetadataAndKeepsItAfterSoftDelete() {
        Inspection inspection = inspectionRepository.saveAndFlush(newInspection(null));
        UUID evidenceId = UUID.randomUUID();
        Instant receivedAt = Instant.parse("2026-08-01T16:12:30Z");

        InspectionEvidence evidence = evidenceRepository.saveAndFlush(InspectionEvidence.create(
                evidenceId,
                inspection.getId(),
                null,
                null,
                "PHOTO",
                "inspections/" + inspection.getId() + "/" + evidenceId + ".jpg",
                "image/jpeg",
                4,
                "aabbccdd",
                "Lacre danificado",
                BigDecimal.valueOf(-23.5),
                BigDecimal.valueOf(-47.4),
                BigDecimal.valueOf(12.5),
                Instant.parse("2026-08-01T13:12:30Z"),
                receivedAt,
                receivedAt,
                technicianId
        ));

        assertThat(evidenceRepository.findById(evidenceId).orElseThrow().getStorageKey())
                .doesNotContain("original");

        evidence.remove(technicianId, Instant.parse("2026-08-01T16:20:00Z"));
        evidenceRepository.saveAndFlush(evidence);

        assertThat(evidenceRepository.findById(evidenceId).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void persistsNonConformityWithOfflineIdAndLinksEvidence() {
        Inspection inspection = inspectionRepository.saveAndFlush(newInspection(null));
        InspectionItemSnapshot snapshot = snapshotRepository.findByInspectionIdOrderBySectionOrderAscItemOrderAsc(
                inspection.getId()
        ).get(0);
        UUID nonConformityId = UUID.randomUUID();

        NonConformity nonConformity = nonConformityRepository.saveAndFlush(NonConformity.create(
                nonConformityId,
                inspection.getId(),
                snapshot.getId(),
                null,
                "Lacre rompido",
                "O lacre foi encontrado rompido.",
                NonConformitySeverity.CRITICAL,
                technicianId,
                Instant.parse("2026-08-01T13:12:30Z"),
                Instant.parse("2026-08-01T16:12:30Z")
        ));

        assertThat(nonConformityRepository.findByIdAndInspectionId(nonConformityId, inspection.getId()))
                .containsSame(nonConformity);
        assertThat(nonConformityRepository.search(
                Set.of(inspection.getId()), null, NonConformitySeverity.CRITICAL, null, clientId,
                PageRequest.of(0, 20)
        ).getContent()).extracting(NonConformity::getId).containsExactly(nonConformityId);

        InspectionEvidence evidence = evidenceRepository.saveAndFlush(InspectionEvidence.create(
                UUID.randomUUID(), inspection.getId(), null, nonConformityId, "PHOTO",
                "inspections/" + inspection.getId() + "/critical.jpg", "image/jpeg", 4, "aabbccdd",
                null, null, null, null, Instant.parse("2026-08-01T13:12:30Z"),
                Instant.parse("2026-08-01T16:12:30Z"), Instant.parse("2026-08-01T16:12:30Z"), technicianId
        ));

        assertThat(evidenceRepository.existsByNonConformityIdAndDeletedAtIsNull(nonConformityId)).isTrue();
        assertThat(evidence.getNonConformityId()).isEqualTo(nonConformityId);
    }

    @Test
    void mobileSpecificationFiltersTechnicianStatusAndUpdatedSince() {
        Inspection mine = inspectionRepository.saveAndFlush(newInspection(null));
        UUID anotherTechnicianId = insertUser("TECHNICIAN");
        Inspection outsideScope = Inspection.create(
                published, clientId, siteId, null, anotherTechnicianId, supervisorId, supervisorId,
                "Outra inspecao", null, InspectionPriority.MEDIUM, Instant.now()
        );
        inspectionRepository.saveAndFlush(outsideScope);

        List<Inspection> result = inspectionRepository.findAll(
                InspectionMobileSpecifications.forTechnician(
                        technicianId,
                        InspectionStatus.ASSIGNED,
                        Instant.now().minus(1, ChronoUnit.DAYS)
                ),
                Sort.by(Sort.Direction.ASC, "scheduledFor")
        );

        assertThat(result).extracting(Inspection::getId).containsExactly(mine.getId());
        assertThat(inspectionRepository.findAll(
                InspectionMobileSpecifications.forTechnician(
                        technicianId, null, Instant.now().plus(1, ChronoUnit.DAYS)
                ),
                Sort.unsorted()
        )).isEmpty();
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
    @ValueSource(strings = {"client_id", "site_id", "template_version_id", "scheduled_for"})
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
    void draftInspectionCanBeCreatedWithoutTechnician() {
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("technician_id", null);
        overrides.put("status", "DRAFT");
        UUID id = insertInspection(overrides);

        assertThat(jdbc.queryForObject(
                "SELECT technician_id FROM inspections WHERE id = ?", UUID.class, id
        )).isNull();
    }

    @Test
    void assignedInspectionStillRequiresTechnician() {
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("technician_id", null);
        assertRejected(overrides);
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
    void executionMethodsPersistTheServerGeneratedPairsAndPositions() {
        Inspection inspection = inspectionRepository.saveAndFlush(newInspection(null));
        Instant startedAtDevice = Instant.parse("2026-10-23T10:02:00Z");
        Instant startedAtServer = Instant.parse("2026-10-23T16:02:00Z");
        inspection.start(
                startedAtDevice,
                startedAtServer,
                FieldPosition.of(
                        new BigDecimal("-23.5"), new BigDecimal("-47.4"), null,
                        Instant.parse("2026-10-23T10:02:03Z")
                )
        );
        inspectionRepository.saveAndFlush(inspection);
        entityManager.clear();

        Inspection started = inspectionRepository.findById(inspection.getId()).orElseThrow();
        assertThat(started.getStatus()).isEqualTo(InspectionStatus.IN_PROGRESS);
        assertThat(started.getStartedAtDevice()).isEqualTo(startedAtDevice);
        assertThat(started.getStartedAtServer()).isEqualTo(startedAtServer);
        assertThat(started.getStartPosition()).isNotNull();

        Instant completedAtDevice = Instant.parse("2026-10-23T10:48:00Z");
        Instant submittedAtServer = Instant.parse("2026-10-23T16:48:00Z");
        started.submit(completedAtDevice, submittedAtServer, null);
        inspectionRepository.saveAndFlush(started);
        entityManager.clear();

        Inspection submitted = inspectionRepository.findById(inspection.getId()).orElseThrow();
        assertThat(submitted.getStatus()).isEqualTo(InspectionStatus.SUBMITTED);
        assertThat(submitted.getCompletedAtDevice()).isEqualTo(completedAtDevice);
        assertThat(submitted.getSubmittedAtServer()).isEqualTo(submittedAtServer);
        assertThat(submitted.getCompletionPosition()).isNull();
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
    void administrativeSpecificationScopesSupervisorsAndAggregatesSnapshotCounts() {
        Instant now = Instant.now();
        Inspection overdue = newInspection(null);
        ReflectionTestUtils.setField(overdue, "scheduledFor", now.minus(1, ChronoUnit.HOURS));
        overdue = inspectionRepository.saveAndFlush(overdue);

        Inspection approved = newInspection(null);
        ReflectionTestUtils.setField(approved, "scheduledFor", now.minus(2, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(approved, "status", InspectionStatus.APPROVED);
        ReflectionTestUtils.setField(approved, "approvedAt", now);
        approved = inspectionRepository.saveAndFlush(approved);

        UUID otherSupervisorId = insertUser("SUPERVISOR");
        Inspection outsideScope = Inspection.create(
                published, clientId, siteId, null, technicianId, otherSupervisorId, supervisorId,
                "Inspecao fora do escopo", null, InspectionPriority.HIGH, now.minus(3, ChronoUnit.HOURS)
        );
        outsideScope = inspectionRepository.saveAndFlush(outsideScope);

        InspectionListQuery query = new InspectionListQuery(
                null, null, null, null, null, null, null, null,
                null, null, true, 0, 20, "scheduledFor,asc"
        );
        Page<Inspection> result = inspectionRepository.findAll(
                InspectionSpecifications.withFilters(query, supervisorId, now),
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).extracting(Inspection::getId)
                .containsExactly(overdue.getId());
        assertThat(result.getContent()).extracting(Inspection::getId)
                .doesNotContain(approved.getId(), outsideScope.getId());
        assertThat(snapshotRepository.countApplicableItems(Set.of(overdue.getId())))
                .singleElement()
                .satisfies(progress -> assertThat(progress.getApplicableItems()).isEqualTo(3));
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

    // ── Field position (app#19) ─────────────────────────────────────────

    @Test
    void persistsAndReadsBackTheStartAndCompletionPositions() {
        Inspection inspection = newInspection(null);
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.SUBMITTED);
        ReflectionTestUtils.setField(inspection, "startedAtDevice", Instant.parse("2026-10-23T13:02:00Z"));
        ReflectionTestUtils.setField(inspection, "completedAtDevice", Instant.parse("2026-10-23T13:48:00Z"));
        inspection.recordStartPosition(FieldPosition.of(
                new BigDecimal("-23.501212"), new BigDecimal("-47.453100"), new BigDecimal("18.40"),
                Instant.parse("2026-10-23T13:02:03Z")
        ));
        inspection.recordCompletionPosition(null);
        UUID id = inspectionRepository.saveAndFlush(inspection).getId();
        entityManager.clear();

        Inspection loaded = inspectionRepository.findById(id).orElseThrow();
        assertThat(loaded.getStartPosition().getLatitude()).isEqualByComparingTo("-23.501212");
        assertThat(loaded.getStartPosition().getLongitude()).isEqualByComparingTo("-47.4531");
        assertThat(loaded.getStartPosition().getAccuracyMeters()).isEqualByComparingTo("18.4");
        assertThat(loaded.getStartPosition().getCapturedAtDevice()).isEqualTo(Instant.parse("2026-10-23T13:02:03Z"));
        assertThat(loaded.getCompletionPosition()).isNull();
    }

    @Test
    void halfAPositionIsRejected() {
        Timestamp started = Timestamp.from(Instant.parse("2026-10-23T13:02:00Z"));

        assertRejected(Map.of(
                "status", "IN_PROGRESS", "started_at_device", started,
                "start_latitude", new BigDecimal("-23.5"), "start_position_captured_at", started
        ));
    }

    @Test
    void positionOutsideTheValidRangeIsRejected() {
        Timestamp started = Timestamp.from(Instant.parse("2026-10-23T13:02:00Z"));

        assertRejected(Map.of(
                "status", "IN_PROGRESS", "started_at_device", started,
                "start_latitude", new BigDecimal("91"), "start_longitude", new BigDecimal("-47.4"),
                "start_position_captured_at", started
        ));
    }

    @Test
    void positionWithoutItsTransitionIsRejected() {
        Timestamp captured = Timestamp.from(Instant.parse("2026-10-23T13:02:00Z"));

        assertRejected(Map.of(
                "completion_latitude", new BigDecimal("-23.5"), "completion_longitude", new BigDecimal("-47.4"),
                "completion_position_captured_at", captured
        ));
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
