package com.fieldops.dashboard.presentation;

import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.dashboard.application.DashboardService;
import com.fieldops.dashboard.application.DashboardSummary;
import com.fieldops.dashboard.application.DashboardInspectionStatusCount;
import com.fieldops.dashboard.application.DashboardNonConformitySeverityCount;
import com.fieldops.inspection.application.InspectionReportService;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
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

import java.time.Instant;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "JWT_SECRET=dashboard-test-secret-012345678901234567890123456789",
        "app.security.jwt.secret=dashboard-test-secret-012345678901234567890123456789"
})
@AutoConfigureMockMvc
class DashboardAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private InspectionReportService inspectionReportService;

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
    void supervisorCanReadDashboardSummary() throws Exception {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        when(dashboardService.summary(from, to)).thenReturn(new DashboardSummary(
                3,
                1,
                1,
                List.of(new DashboardInspectionStatusCount(InspectionStatus.ASSIGNED, 3)),
                List.of(new DashboardNonConformitySeverityCount(NonConformitySeverity.HIGH, 2))
        ));

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInspections").value(3))
                .andExpect(jsonPath("$.overdueInspections").value(1))
                .andExpect(jsonPath("$.inspectionsAwaitingReview").value(1))
                .andExpect(jsonPath("$.inspectionsByStatus[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$.nonConformitiesBySeverity[0].count").value(2));
    }

    @Test
    void technicianCannotReadAdministrativeDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/inspections-by-status")
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorCanExportInspectionReportAndTechnicianCannot() throws Exception {
        byte[] pdf = "%PDF-1.7 report".getBytes(StandardCharsets.US_ASCII);
        when(inspectionReportService.generate(any())).thenReturn(pdf);

        mockMvc.perform(get("/api/v1/inspections/{id}/report.pdf", java.util.UUID.randomUUID())
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        mockMvc.perform(get("/api/v1/inspections/{id}/report.pdf", java.util.UUID.randomUUID())
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
    }
}
