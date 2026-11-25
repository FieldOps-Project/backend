package com.fieldops.integration;

import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.domain.model.EquipmentStatus;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.sync.application.SyncConflictResolver;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests covering the 11 critical FieldOps API flows
 * and role-based access enforcement against a real PostgreSQL database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CriticalFlowsIntegrationTest {

    private static final String TEST_PASSWORD = "FieldOps@2026!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @TempDir
    static Path evidenceStorageDir;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> "critical-flows-integration-secret-key-0123456789");
        registry.add("app.security.jwt.secret", () -> "critical-flows-integration-secret-key-0123456789");
        registry.add("app.evidence.storage-dir", () -> evidenceStorageDir.toAbsolutePath().toString());
        registry.add("app.evidence.max-size-bytes", () -> "4096");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InspectionSiteRepository siteRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private InspectionTemplateRepository templateRepository;

    @Autowired
    private InspectionTemplateVersionRepository versionRepository;

    @Autowired
    private InspectionRepository inspectionRepository;

    private User admin;
    private User supervisor;
    private User technicianA;
    private User technicianB;
    private String adminToken;
    private String supervisorToken;
    private String techAToken;
    private String techBToken;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String hash = passwordEncoder.encode(TEST_PASSWORD);

        admin = userRepository.saveAndFlush(
                User.create("Admin " + suffix, "admin-" + suffix + "@fieldops.local", hash, UserRole.ADMIN, UserStatus.ACTIVE, null)
        );
        supervisor = userRepository.saveAndFlush(
                User.create("Supervisor " + suffix, "sup-" + suffix + "@fieldops.local", hash, UserRole.SUPERVISOR, UserStatus.ACTIVE, null)
        );
        technicianA = userRepository.saveAndFlush(
                User.create("Tecnico A " + suffix, "techa-" + suffix + "@fieldops.local", hash, UserRole.TECHNICIAN, UserStatus.ACTIVE, null)
        );
        technicianB = userRepository.saveAndFlush(
                User.create("Tecnico B " + suffix, "techb-" + suffix + "@fieldops.local", hash, UserRole.TECHNICIAN, UserStatus.ACTIVE, null)
        );

        adminToken = login(admin.getEmail());
        supervisorToken = login(supervisor.getEmail());
        techAToken = login(technicianA.getEmail());
        techBToken = login(technicianB.getEmail());
    }

    // ── Flow 1: Auth & 401 without token ─────────────────────────────────

    @Test
    void flow01_loginAndTokenAccessAndUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/inspections"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + techAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(technicianA.getEmail()))
                .andExpect(jsonPath("$.role").value("TECHNICIAN"));
    }

    // ── Flow 2: Scheduling & frozen snapshot ─────────────────────────────

    @Test
    void flow02_fullSchedulingFreezesChecklistSnapshotWithAllItems() throws Exception {
        FixtureData fixture = createPublishedFixture("Flow02");

        String body = """
                {
                  "templateVersionId": "%s",
                  "clientId": "%s",
                  "siteId": "%s",
                  "equipmentId": "%s",
                  "technicianId": "%s",
                  "supervisorId": "%s",
                  "title": "Inspecao Preventiva Gerador",
                  "instructions": "Seguir procedimento padrao",
                  "priority": "HIGH",
                  "scheduledFor": "%s"
                }
                """.formatted(
                fixture.version.getId(),
                fixture.client.getId(),
                fixture.site.getId(),
                fixture.equipment.getId(),
                technicianA.getId(),
                supervisor.getId(),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/inspections")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.itemSnapshots.length()").value(2))
                .andExpect(jsonPath("$.itemSnapshots[0].itemTitle").value("Item 1"))
                .andExpect(jsonPath("$.itemSnapshots[1].itemTitle").value("Item 2"))
                .andReturn();

        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID inspectionId = UUID.fromString(created.get("id").asString());
        Integer snapCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inspection_item_snapshots WHERE inspection_id = ?",
                Integer.class,
                inspectionId
        );
        assertThat(snapCount).isEqualTo(2);
    }

    // ── Flow 3: Template publication validation ──────────────────────────

    @Test
    void flow03_invalidPublicationIsBlockedAndValidPublicationCreatesVersion() throws Exception {
        MvcResult templateResult = mockMvc.perform(post("/api/v1/inspection-templates")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Checklist Flow03 %s",
                                  "description": "Validacao de publicacao",
                                  "category": "Eletrica"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        UUID templateId = UUID.fromString(objectMapper.readTree(templateResult.getResponse().getContentAsString())
                .get("id").asString());

        // Empty draft cannot be published -> 422
        mockMvc.perform(post("/api/v1/inspection-templates/" + templateId + "/publish")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_PUBLISH_INVALID"));

        // Add section and item
        MvcResult sectionResult = mockMvc.perform(post("/api/v1/inspection-templates/" + templateId + "/sections")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Secao 1", "displayOrder": 1 }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID sectionId = UUID.fromString(objectMapper.readTree(sectionResult.getResponse().getContentAsString())
                .get("id").asString());

        mockMvc.perform(post("/api/v1/inspection-templates/" + templateId + "/sections/" + sectionId + "/items")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "EL-01",
                                  "title": "Quadros aterrados?",
                                  "responseType": "BOOLEAN",
                                  "required": true,
                                  "displayOrder": 1
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/inspection-templates/" + templateId + "/publish")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1));
    }

    // ── Flow 4 & 5: Start -> Responses -> Submit (valid & 422 pending) ───

    @Test
    void flow04And05_startSubmitValidationAndValidSubmission() throws Exception {
        FixtureData fixture = createPublishedFixture("Flow04");
        Inspection inspection = createAssignedInspection(fixture, technicianA);
        List<UUID> itemIds = inspection.getItemSnapshots().stream().map(s -> s.getId()).toList();
        Instant now = Instant.parse("2026-11-24T10:00:00Z");

        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/start")
                        .header("Authorization", "Bearer " + techAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "startedAtDevice": "%s", "location": null }
                                """.formatted(now)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Answer only 1st item
        saveBooleanResponse(inspection.getId(), UUID.randomUUID(), itemIds.get(0), true, now, 0);

        // Flow 5: Submit with pending required item -> 422 with list
        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/submit")
                        .header("Authorization", "Bearer " + techAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "completedAtDevice": "%s", "location": null }
                                """.formatted(now.plusSeconds(60))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        // Answer 2nd required item and submit -> Flow 4: 200 SUBMITTED
        saveBooleanResponse(inspection.getId(), UUID.randomUUID(), itemIds.get(1), true, now.plusSeconds(90), 0);

        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/submit")
                        .header("Authorization", "Bearer " + techAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "completedAtDevice": "%s", "location": null }
                                """.formatted(now.plusSeconds(120))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    // ── Flow 6: Idempotent push x3 (asserts DB row count) ────────────────

    @Test
    void flow06_pushSameBatchThreeTimesCreatesNoDuplicatesInDatabase() throws Exception {
        FixtureData fixture = createPublishedFixture("Flow06");
        Inspection inspection = createInProgressInspection(fixture, technicianA);
        UUID itemId = inspection.getItemSnapshots().getFirst().getId();
        UUID operationId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        String pushPayload = """
                {
                  "deviceId": "%s",
                  "lastPullCursor": null,
                  "operations": [{
                    "operationId": "%s",
                    "entityType": "INSPECTION_RESPONSE",
                    "entityId": "%s",
                    "operationType": "UPSERT",
                    "baseVersion": 0,
                    "dependsOn": [],
                    "payload": {
                      "inspectionId": "%s",
                      "inspectionItemId": "%s",
                      "valueBoolean": true,
                      "answeredAtDevice": "2026-11-24T10:00:00Z"
                    }
                  }]
                }
                """.formatted(deviceId, operationId, responseId, inspection.getId(), itemId);

        mockMvc.perform(post("/api/v1/mobile/sync/push")
                        .header("Authorization", "Bearer " + techAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pushPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/mobile/sync/push")
                            .header("Authorization", "Bearer " + techAToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pushPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].status").value("ALREADY_APPLIED"));
        }

        Integer responseCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inspection_responses WHERE inspection_id = ?",
                Integer.class,
                inspection.getId()
        );
        Integer syncOpCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sync_processed_operations WHERE operation_id = ?",
                Integer.class,
                operationId
        );
        assertThat(responseCount).isEqualTo(1);
        assertThat(syncOpCount).isEqualTo(1);
    }

    // ── Flow 7: Stale baseVersion -> CONFLICT ────────────────────────────

    @Test
    void flow07_pushWithStaleBaseVersionReturnsConflictWithServerState() throws Exception {
        FixtureData fixture = createPublishedFixture("Flow07");
        Inspection inspection = createInProgressInspection(fixture, technicianA);
        UUID itemId = inspection.getItemSnapshots().getFirst().getId();
        UUID responseId = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-11-24T10:00:00Z");

        saveBooleanResponse(inspection.getId(), responseId, itemId, true, t0, 0);
        // Server update bumps version to 1
        saveBooleanResponse(inspection.getId(), responseId, itemId, false, t0.plusSeconds(30), 0);

        String stalePush = """
                {
                  "deviceId": "%s",
                  "lastPullCursor": null,
                  "operations": [{
                    "operationId": "%s",
                    "entityType": "INSPECTION_RESPONSE",
                    "entityId": "%s",
                    "operationType": "UPSERT",
                    "baseVersion": 0,
                    "dependsOn": [],
                    "payload": {
                      "inspectionId": "%s",
                      "inspectionItemId": "%s",
                      "valueBoolean": true,
                      "answeredAtDevice": "2026-11-24T10:01:00Z"
                    }
                  }]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), responseId, inspection.getId(), itemId);

        mockMvc.perform(post("/api/v1/mobile/sync/push")
                        .header("Authorization", "Bearer " + techAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stalePush))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("CONFLICT"))
                .andExpect(jsonPath("$.results[0].entityVersion").value(1))
                .andExpect(jsonPath("$.results[0].errorCode").value(SyncConflictResolver.CODE_VERSION_CONFLICT))
                .andExpect(jsonPath("$.results[0].serverState.valueBoolean").value(false));
    }

    // ── Flow 8: Failed dependency -> DEPENDENCY_FAILED ───────────────────

    @Test
    void flow08_pushWithFailedDependencyMarksDependentOperationsAsDependencyFailed() throws Exception {
        FixtureData fixture = createPublishedFixture("Flow08");
        Inspection inspection = createInProgressInspection(fixture, technicianA);
        UUID failedOpId = UUID.randomUUID();
        UUID dependentOpId = UUID.randomUUID();

        String pushPayload = """
                {
                  "deviceId": "%s",
                  "lastPullCursor": null,
                  "operations": [
                    {
                      "operationId": "%s",
                      "entityType": "INSPECTION_RESPONSE",
                      "entityId": "%s",
                      "operationType": "UPSERT",
                      "baseVersion": 0,
                      "dependsOn": [],
                      "payload": {
                        "inspectionId": "%s",
                        "inspectionItemId": "%s",
                        "valueBoolean": true,
                        "answeredAtDevice": "2026-11-24T10:00:00Z"
                      }
                    },
                    {
                      "operationId": "%s",
                      "entityType": "INSPECTION_EVIDENCE",
                      "entityId": "%s",
                      "operationType": "CREATE",
                      "baseVersion": 0,
                      "dependsOn": ["%s"],
                      "payload": {
                        "inspectionId": "%s",
                        "type": "PHOTO",
                        "mimeType": "image/jpeg",
                        "sizeBytes": 128,
                        "checksum": "%s"
                      }
                    }
                  ]
                }
                """.formatted(
                UUID.randomUUID(),
                failedOpId,
                UUID.randomUUID(),
                inspection.getId(),
                UUID.randomUUID(), // invalid snapshot item -> REJECTED
                dependentOpId,
                UUID.randomUUID(),
                failedOpId,
                inspection.getId(),
                "a".repeat(64)
        );

        mockMvc.perform(post("/api/v1/mobile/sync/push")
                        .header("Authorization", "Bearer " + techAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pushPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[1].status").value("DEPENDENCY_FAILED"));
    }

    // ── Flow 9: Review -> reject without reason (422) -> reject -> reopen -> approve ──

    @Test
    void flow09_reviewRejectionReopenAndApprovalCycle() throws Exception {
        FixtureData fixture = createPublishedFixture("Flow09");
        Inspection inspection = createInProgressInspection(fixture, technicianA);
        List<UUID> itemIds = inspection.getItemSnapshots().stream().map(s -> s.getId()).toList();
        Instant now = Instant.parse("2026-11-24T10:00:00Z");

        UUID resp1Id = UUID.randomUUID();
        saveBooleanResponse(inspection.getId(), resp1Id, itemIds.get(0), true, now, 0);
        saveBooleanResponse(inspection.getId(), UUID.randomUUID(), itemIds.get(1), true, now, 0);

        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/submit")
                        .header("Authorization", "Bearer " + techAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "completedAtDevice": "%s", "location": null }
                                """.formatted(now.plusSeconds(30))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/begin-review")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        // Reject without reason -> 422
        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/reject")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "   ", "itemsToCorrect": ["%s"] }
                                """.formatted(itemIds.get(0))))
                .andExpect(status().isUnprocessableEntity());

        // Reject with reason -> 200
        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/reject")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Reavaliar item 1", "itemsToCorrect": ["%s"] }
                                """.formatted(itemIds.get(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECTED"));

        // Reopen -> update corrected item -> resubmit
        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/reopen")
                        .header("Authorization", "Bearer " + techAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        saveBooleanResponse(inspection.getId(), resp1Id, itemIds.get(0), false, now.plusSeconds(120), 0);

        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/submit")
                        .header("Authorization", "Bearer " + techAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "completedAtDevice": "%s", "location": null }
                                """.formatted(now.plusSeconds(180))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/begin-review")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/inspections/" + inspection.getId() + "/approve")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "comments": "Aprovado apos correcao" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"));
    }

    // ── Flow 10: Horizontal isolation (Tech B accessing Tech A -> 404) ───

    @Test
    void flow10_technicianCannotAccessAnotherTechniciansInspection() throws Exception {
        FixtureData fixture = createPublishedFixture("Flow10");
        Inspection inspectionA = createAssignedInspection(fixture, technicianA);

        mockMvc.perform(get("/api/v1/mobile/inspections/" + inspectionA.getId())
                        .header("Authorization", "Bearer " + techBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/inspections/" + inspectionA.getId() + "/start")
                        .header("Authorization", "Bearer " + techBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "startedAtDevice": "2026-11-24T10:00:00Z", "location": null }
                                """))
                .andExpect(status().isNotFound());
    }

    // ── Flow 11: Evidence upload (415, 413, 201) ─────────────────────────

    @Test
    void flow11_evidenceUploadValidatesFormatSizeAndSucceeds() throws Exception {
        FixtureData fixture = createPublishedFixture("Flow11");
        Inspection inspection = createInProgressInspection(fixture, technicianA);

        MockMultipartFile metadataPart = new MockMultipartFile(
                "metadata",
                "metadata.json",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {
                          "id": "%s",
                          "type": "PHOTO",
                          "capturedAtDevice": "2026-11-24T10:00:00Z",
                          "description": "Foto equipamento"
                        }
                        """.formatted(UUID.randomUUID()).getBytes(StandardCharsets.UTF_8)
        );

        // 415 Unsupported Media Type
        MockMultipartFile invalidFormat = new MockMultipartFile(
                "file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "not an image".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/v1/inspections/" + inspection.getId() + "/evidence")
                        .file(metadataPart)
                        .file(invalidFormat)
                        .header("Authorization", "Bearer " + techAToken))
                .andExpect(status().isUnsupportedMediaType());

        // 413 Payload Too Large (> 4096 bytes configured in test properties)
        byte[] oversizedJpeg = new byte[5000];
        oversizedJpeg[0] = (byte) 0xff;
        oversizedJpeg[1] = (byte) 0xd8;
        oversizedJpeg[2] = (byte) 0xff;
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.jpg", MediaType.IMAGE_JPEG_VALUE, oversizedJpeg);
        mockMvc.perform(multipart("/api/v1/inspections/" + inspection.getId() + "/evidence")
                        .file(metadataPart)
                        .file(largeFile)
                        .header("Authorization", "Bearer " + techAToken))
                .andExpect(status().isPayloadTooLarge());

        // 201 Created with valid JPEG
        byte[] validJpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01, 0x02, 0x03};
        MockMultipartFile validFile = new MockMultipartFile("file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, validJpeg);
        mockMvc.perform(multipart("/api/v1/inspections/" + inspection.getId() + "/evidence")
                        .file(metadataPart)
                        .file(validFile)
                        .header("Authorization", "Bearer " + techAToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"));
    }

    // ── Role authorization matrix across profiles ────────────────────────

    @Test
    void roleAuthorizationRejectsUnauthorizedProfilesWith403() throws Exception {
        // TECHNICIAN cannot manage users, clients or schedule inspections
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + techAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/inspections")
                        .header("Authorization", "Bearer " + techAToken))
                .andExpect(status().isForbidden());

        // SUPERVISOR cannot manage users (ADMIN only)
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());

        // ADMIN cannot execute mobile technician routes
        mockMvc.perform(get("/api/v1/mobile/inspections")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(email, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asString();
    }

    private void saveBooleanResponse(
            UUID inspectionId,
            UUID responseId,
            UUID itemId,
            boolean value,
            Instant answeredAtDevice,
            int baseVersion
    ) throws Exception {
        mockMvc.perform(put("/api/v1/inspections/" + inspectionId + "/responses/" + responseId)
                        .header("Authorization", "Bearer " + techAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "%s",
                                  "inspectionItemId": "%s",
                                  "valueBoolean": %s,
                                  "answeredAtDevice": "%s",
                                  "baseVersion": %d
                                }
                                """.formatted(responseId, itemId, value, answeredAtDevice, baseVersion)))
                .andExpect(status().isOk());
    }

    private FixtureData createPublishedFixture(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Client client = clientRepository.saveAndFlush(
                Client.create(prefix + " Client " + uid, null, null, null, null, ClientStatus.ACTIVE)
        );
        InspectionSite site = siteRepository.saveAndFlush(
                InspectionSite.create(client, prefix + " Site " + uid, null, "Rua A, 10", "SP", "SP", null, null, null, null, null, InspectionSiteStatus.ACTIVE)
        );
        Equipment equipment = equipmentRepository.saveAndFlush(
                Equipment.create(site.getId(), prefix + " Equip " + uid, null, null, null, null, null, "QR-" + prefix + "-" + uid, null, EquipmentStatus.ACTIVE)
        );
        InspectionTemplate template = templateRepository.saveAndFlush(
                InspectionTemplate.create(prefix + " Template " + uid, null, "Geral", supervisor.getId())
        );
        InspectionTemplateVersion version = InspectionTemplateVersion.createPublished(
                template, 1, prefix + " Template", null, supervisor.getId()
        );
        TemplateSection section = TemplateSection.create(version, "Secao", null, 1);
        section.addItem(TemplateItem.create(section, "IT-01", "Item 1", null, ResponseType.BOOLEAN, true, false, false, null, 1));
        section.addItem(TemplateItem.create(section, "IT-02", "Item 2", null, ResponseType.BOOLEAN, true, false, false, null, 2));
        version.addSection(section);
        version = versionRepository.saveAndFlush(version);
        return new FixtureData(client, site, equipment, version);
    }

    private Inspection createAssignedInspection(FixtureData fixture, User technician) {
        Inspection inspection = Inspection.create(
                fixture.version, fixture.client.getId(), fixture.site.getId(), fixture.equipment.getId(),
                technician.getId(), supervisor.getId(), supervisor.getId(),
                "Inspecao " + UUID.randomUUID(), null, InspectionPriority.MEDIUM,
                Instant.now().plus(1, ChronoUnit.DAYS)
        );
        return inspectionRepository.saveAndFlush(inspection);
    }

    private Inspection createInProgressInspection(FixtureData fixture, User technician) {
        Inspection inspection = Inspection.create(
                fixture.version, fixture.client.getId(), fixture.site.getId(), fixture.equipment.getId(),
                technician.getId(), supervisor.getId(), supervisor.getId(),
                "Inspecao " + UUID.randomUUID(), null, InspectionPriority.MEDIUM,
                Instant.now().plus(1, ChronoUnit.DAYS)
        );
        inspection.start(Instant.now(), Instant.now(), null);
        return inspectionRepository.saveAndFlush(inspection);
    }

    private record FixtureData(
            Client client,
            InspectionSite site,
            Equipment equipment,
            InspectionTemplateVersion version
    ) {
    }
}
