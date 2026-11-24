package com.fieldops.user.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionItemSnapshotRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionReviewRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.sync.infrastructure.persistence.SyncProcessedOperationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateItemRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateSectionRepository;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "JWT_SECRET=local-test-secret-012345678901234567890123456789",
        "app.security.jwt.secret=local-test-secret-012345678901234567890123456789"
})
@AutoConfigureMockMvc
class UserAuthorizationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private AuditEventRepository auditEventRepository;

    @MockitoBean
    private AuditEventService auditEventService;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private InspectionSiteRepository inspectionSiteRepository;

    @MockitoBean
    private EquipmentRepository equipmentRepository;

    @MockitoBean
    private InspectionTemplateRepository inspectionTemplateRepository;

    @MockitoBean
    private InspectionTemplateVersionRepository inspectionTemplateVersionRepository;

    @MockitoBean
    private TemplateSectionRepository templateSectionRepository;

    @MockitoBean
    private TemplateItemRepository templateItemRepository;

    @MockitoBean
    private InspectionRepository inspectionRepository;

    @MockitoBean
    private InspectionReviewRepository inspectionReviewRepository;

    @MockitoBean
    private InspectionItemSnapshotRepository inspectionItemSnapshotRepository;

    @MockitoBean
    private InspectionResponseRepository inspectionResponseRepository;

    @MockitoBean
    private InspectionEvidenceRepository inspectionEvidenceRepository;

    @MockitoBean
    private NonConformityRepository nonConformityRepository;

    @MockitoBean
    private SyncProcessedOperationRepository syncProcessedOperationRepository;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.create(
                "Maria Silva",
                "maria@example.com",
                "$2a$10$secretHash",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadAndManageUsers() {
        assertThat(userService.findById(userId)).isSameAs(user);

        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User created = userService.createUser(
                "Joao Silva",
                "joao@example.com",
                "StrongPassword",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        );

        assertThat(created.getEmail()).isEqualTo("joao@example.com");
        verify(userRepository).findById(userId);
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisorCanReadButCannotManageUsers() {
        assertThat(userService.findById(userId)).isSameAs(user);

        assertThatThrownBy(() -> userService.createUser(
                "Joao Silva",
                "joao@example.com",
                "StrongPassword",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        )).isInstanceOf(AccessDeniedException.class);

        verify(userRepository).findById(userId);
    }

    @Test
    @WithMockUser(roles = "TECHNICIAN")
    void technicianCannotReadOrManageUsers() {
        assertThatThrownBy(() -> userService.findById(userId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.updateStatus(userId, UserStatus.BLOCKED))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    @WithMockUser(roles = "CLIENT_VIEWER")
    void clientViewerCannotReadOrManageUsers() {
        assertThatThrownBy(() -> userService.findById(userId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    @WithMockUser(roles = "TECHNICIAN")
    void technicianGetsForbiddenFromAdministrativeUserEndpoint() throws Exception {
        mockMvc.perform(get("/users/{id}", userId)
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorGetsForbiddenFromMobileInspectionEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/inspections")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorGetsForbiddenFromTechnicianExecutionEndpoints() throws Exception {
        UUID inspectionId = UUID.randomUUID();
        String payload = "{\"startedAtDevice\":\"2026-11-23T10:02:00Z\"}";

        mockMvc.perform(post("/api/v1/inspections/{id}/start", inspectionId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/inspections/{id}/submit", inspectionId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completedAtDevice\":\"2026-11-23T10:48:00Z\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorGetsForbiddenFromInspectionResponseEndpoints() throws Exception {
        UUID inspectionId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        String payload = "{\"id\":\"" + responseId
                + "\",\"inspectionItemId\":\"" + UUID.randomUUID()
                + "\",\"valueBoolean\":true,\"answeredAtDevice\":\"2026-11-23T10:02:00Z\",\"baseVersion\":0}";

        mockMvc.perform(get("/api/v1/inspections/{id}/responses", inspectionId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(put("/api/v1/inspections/{inspectionId}/responses/{responseId}", inspectionId, responseId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/inspections/{id}/responses:batch", inspectionId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responses\":[" + payload + "]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorGetsForbiddenFromEvidenceReadAndDeleteEndpoints() throws Exception {
        UUID inspectionId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/inspections/{id}/evidence", inspectionId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/evidence/{id}", evidenceId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/evidence/{id}", evidenceId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisorGetsForbiddenFromUserManagementEndpoint() throws Exception {
        mockMvc.perform(post("/users")
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Joao Silva",
                                  "email": "joao@example.com",
                                  "password": "StrongPassword",
                                  "role": "TECHNICIAN",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListUsersThroughTheVersionedEndpoint() throws Exception {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));

        mockMvc.perform(get("/api/v1/users")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidRoleFilterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users?role=UNKNOWN")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidRoleInCreateReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Joao Silva",
                                  "email": "joao@example.com",
                                  "password": "StrongPassword",
                                  "role": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanResetPasswordThroughTheVersionedEndpoint() throws Exception {
        when(userRepository.save(user)).thenReturn(user);

        mockMvc.perform(post("/api/v1/users/{id}/reset-password", userId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isString())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
