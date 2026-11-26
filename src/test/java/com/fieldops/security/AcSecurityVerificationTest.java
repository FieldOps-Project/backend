package com.fieldops.security;

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
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Systematic verification of AC-SECURITY criteria across the FieldOps API:
 * unauthenticated rejection (401), RBAC enforcement (403), horizontal isolation (404),
 * evidence protection, CORS origin restriction, and safe error responses.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "JWT_SECRET=ac-security-verification-secret-01234567890123456789",
        "app.security.jwt.secret=ac-security-verification-secret-01234567890123456789",
        "app.cors.allowed-origins=https://admin.fieldops.com.br"
})
@AutoConfigureMockMvc
class AcSecurityVerificationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void criterion1_allProtectedEndpointsRejectUnauthenticatedRequestsWith401() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sites")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/equipment")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/equipment/by-qr/QR-1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inspection-templates")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inspections")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inspections/" + id + "/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inspections/" + id + "/reviews")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/mobile/inspections")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/mobile/inspections/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inspections/" + id + "/responses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inspections/" + id + "/evidence")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/evidence/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/evidence/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/non-conformities")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/mobile/sync/pull")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/mobile/sync/push")).andExpect(status().isUnauthorized());
    }

    @Test
    void criterion2And4_technicianHorizontalIsolationReturns404AcrossResourcesAndEvidence() throws Exception {
        Authentication techBAuth = authAs(UserRole.TECHNICIAN);
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(any(), any())).thenReturn(Optional.empty());
        when(inspectionRepository.findById(any())).thenReturn(Optional.empty());
        when(inspectionEvidenceRepository.findById(any())).thenReturn(Optional.empty());
        when(nonConformityRepository.findById(any())).thenReturn(Optional.empty());

        UUID otherInspectionId = UUID.randomUUID();
        UUID otherEvidenceId = UUID.randomUUID();
        UUID otherNcId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/mobile/inspections/" + otherInspectionId)
                        .with(authentication(techBAuth)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/inspections/" + otherInspectionId + "/responses")
                        .with(authentication(techBAuth)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/inspections/" + otherInspectionId + "/evidence")
                        .with(authentication(techBAuth)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/evidence/" + otherEvidenceId)
                        .with(authentication(techBAuth)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/evidence/" + otherEvidenceId)
                        .with(authentication(techBAuth)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/non-conformities/" + otherNcId)
                        .with(authentication(techBAuth)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/inspections/" + otherInspectionId + "/history")
                        .with(authentication(techBAuth)))
                .andExpect(status().isNotFound());
    }

    @Test
    void criterion3And8_nonAdminAndWrongRolesReceive403ServerSide() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/users").with(user("tech").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users").with(user("sup").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Test", "email": "t@fieldops.local", "password": "Password@123", "role": "TECHNICIAN" }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/clients").with(user("tech").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/sites").with(user("tech").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/equipment").with(user("tech").roles("TECHNICIAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "siteId": "%s", "name": "Eq", "qrCode": "QR-99" }
                                """.formatted(id)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/inspection-templates").with(user("tech").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inspections/" + id + "/approve").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/inspections/" + id + "/responses/" + id)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "id": "%s", "inspectionItemId": "%s", "valueBoolean": true, "answeredAtDevice": "2026-11-24T10:00:00Z", "baseVersion": 0 }
                                """.formatted(id, id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void criterion6_errorResponsesNeverExposeStackTracesSqlOrInternalClasses() throws Exception {
        Authentication techAuth = authAs(UserRole.TECHNICIAN);
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(any(), any()))
                .thenThrow(new RuntimeException("SELECT * FROM users WHERE secret_key = 'leak'"));

        MvcResult result = mockMvc.perform(get("/api/v1/mobile/inspections/" + UUID.randomUUID())
                        .with(authentication(techAuth)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SELECT * FROM");
        assertThat(body).doesNotContain("secret_key");
        assertThat(body).doesNotContain("java.lang.");
        assertThat(body).doesNotContain("com.fieldops.");
    }

    @Test
    void criterion7_corsPreflightAllowsOnlyConfiguredOriginAndRejectsOthers() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://admin.fieldops.com.br")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://admin.fieldops.com.br"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private Authentication authAs(UserRole role) {
        UUID userId = UUID.randomUUID();
        User domainUser = User.create(
                "User " + role.name(),
                role.name().toLowerCase() + "@fieldops.local",
                "hash",
                role,
                UserStatus.ACTIVE,
                null
        );
        ReflectionTestUtils.setField(domainUser, "id", userId);
        return new UsernamePasswordAuthenticationToken(
                domainUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }
}
