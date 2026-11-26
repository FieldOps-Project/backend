package com.fieldops.openapi;

import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionItemSnapshotRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionReviewRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateItemRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateSectionRepository;
import com.fieldops.sync.infrastructure.persistence.SyncProcessedOperationRepository;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "JWT_SECRET=openapi-snapshot-test-secret-01234567890123456789",
        "app.security.jwt.secret=openapi-snapshot-test-secret-01234567890123456789"
})
@AutoConfigureMockMvc
class OpenApiSnapshotTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private RefreshTokenRepository refreshTokenRepository;
    @MockitoBean private AuditEventRepository auditEventRepository;
    @MockitoBean private ClientRepository clientRepository;
    @MockitoBean private InspectionSiteRepository inspectionSiteRepository;
    @MockitoBean private EquipmentRepository equipmentRepository;
    @MockitoBean private InspectionTemplateRepository inspectionTemplateRepository;
    @MockitoBean private InspectionTemplateVersionRepository inspectionTemplateVersionRepository;
    @MockitoBean private TemplateSectionRepository templateSectionRepository;
    @MockitoBean private TemplateItemRepository templateItemRepository;
    @MockitoBean private InspectionRepository inspectionRepository;
    @MockitoBean private InspectionReviewRepository inspectionReviewRepository;
    @MockitoBean private InspectionItemSnapshotRepository inspectionItemSnapshotRepository;
    @MockitoBean private InspectionResponseRepository inspectionResponseRepository;
    @MockitoBean private InspectionEvidenceRepository inspectionEvidenceRepository;
    @MockitoBean private NonConformityRepository nonConformityRepository;
    @MockitoBean private SyncProcessedOperationRepository syncProcessedOperationRepository;
    @MockitoBean private JdbcTemplate jdbcTemplate;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void generatedOpenApiCoversAllEndpointsAndMatchesVersionedSnapshot() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String rawJson = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode paths = root.get("paths");
        assertThat(paths).isNotNull();

        String[] requiredPaths = {
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/me",
                "/api/v1/users",
                "/api/v1/clients",
                "/api/v1/sites",
                "/api/v1/equipment",
                "/api/v1/equipment/by-qr/{qrCode}",
                "/api/v1/inspection-templates",
                "/api/v1/inspection-templates/{id}/publish",
                "/api/v1/inspections",
                "/api/v1/inspections/{id}/assign",
                "/api/v1/inspections/{id}/cancel",
                "/api/v1/inspections/{id}/start",
                "/api/v1/inspections/{id}/submit",
                "/api/v1/inspections/{id}/reopen",
                "/api/v1/inspections/{inspectionId}/responses",
                "/api/v1/inspections/{inspectionId}/responses:batch",
                "/api/v1/inspections/{inspectionId}/evidence",
                "/api/v1/evidence/{id}",
                "/api/v1/non-conformities",
                "/api/v1/inspections/{inspectionId}/non-conformities",
                "/api/v1/inspections/{inspectionId}/reviews",
                "/api/v1/inspections/{inspectionId}/begin-review",
                "/api/v1/inspections/{inspectionId}/approve",
                "/api/v1/inspections/{inspectionId}/reject",
                "/api/v1/inspections/{id}/history",
                "/api/v1/mobile/inspections",
                "/api/v1/mobile/inspections/{id}",
                "/api/v1/mobile/sync/pull",
                "/api/v1/mobile/sync/push"
        };
        for (String path : requiredPaths) {
            assertThat(paths.has(path)).as("OpenAPI path %s must exist", path).isTrue();
        }

        String formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n";
        Path snapshotPath = Path.of("openapi/openapi.json");
        if (Boolean.getBoolean("updateOpenApi")) {
            Files.createDirectories(snapshotPath.getParent());
            Files.writeString(snapshotPath, formatted, StandardCharsets.UTF_8);
        }

        assertThat(Files.exists(snapshotPath)).isTrue();
        JsonNode committed = objectMapper.readTree(Files.readString(snapshotPath, StandardCharsets.UTF_8));
        assertThat(committed.get("paths").propertyNames())
                .containsExactlyInAnyOrderElementsOf(paths.propertyNames());
        assertThat(committed.get("info").get("description").asString())
                .contains("Máquina de Estados da Inspeção")
                .contains("Política de Conflitos");
    }
}
