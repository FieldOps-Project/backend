package com.fieldops.inspection.application;

import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateItemRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateSectionRepository;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "JWT_SECRET=local-test-secret-012345678901234567890123456789",
        "app.security.jwt.secret=local-test-secret-012345678901234567890123456789"
})
@AutoConfigureMockMvc
class InspectionReviewAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InspectionTemplateRepository inspectionTemplateRepository;

    @MockitoBean
    private InspectionTemplateVersionRepository inspectionTemplateVersionRepository;

    @MockitoBean
    private TemplateSectionRepository templateSectionRepository;

    @MockitoBean
    private TemplateItemRepository templateItemRepository;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private InspectionSiteRepository inspectionSiteRepository;

    @MockitoBean
    private EquipmentRepository equipmentRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private AuditEventRepository auditEventRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void technicianCannotApproveAnInspection() throws Exception {
        mockMvc.perform(post("/api/v1/inspections/{id}/approve", UUID.randomUUID())
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void adminCannotRejectAnInspection() throws Exception {
        mockMvc.perform(post("/api/v1/inspections/{id}/reject", UUID.randomUUID())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Foto ilegivel\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotBeginReviewOrReadReviewCycles() throws Exception {
        UUID inspectionId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/inspections/{id}/begin-review", inspectionId)
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/inspections/{id}/reviews", inspectionId)
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
    }
}
