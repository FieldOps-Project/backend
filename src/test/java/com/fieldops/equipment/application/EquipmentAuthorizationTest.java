package com.fieldops.equipment.application;

import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.domain.model.EquipmentStatus;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
class EquipmentAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EquipmentRepository equipmentRepository;

    @MockitoBean
    private InspectionSiteRepository inspectionSiteRepository;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private AuditEventRepository auditEventRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private UUID siteId;
    private UUID equipmentId;
    private InspectionSite activeSite;
    private Equipment equipment;

    @BeforeEach
    void setUp() {
        siteId = UUID.randomUUID();
        equipmentId = UUID.randomUUID();
        Client client = Client.create("Client Alpha", "Legal Alpha", "12345678000190", "a@alpha.com", "11999990000", ClientStatus.ACTIVE);
        activeSite = InspectionSite.create(client, "Site 1", "Desc", "Addr", "City", "SP", "14000-000", null, null, "Contact", "Phone", InspectionSiteStatus.ACTIVE);
        equipment = Equipment.create(siteId, "Transformador T1", "AST-T1", "SN-T1", "WEG", "T-500", "Transformador 500kVA", "QR-WEG-T1", EquipmentStatus.ACTIVE, null);

        when(inspectionSiteRepository.findById(siteId)).thenReturn(Optional.of(activeSite));
        when(equipmentRepository.findById(equipmentId)).thenReturn(Optional.of(equipment));
        when(equipmentRepository.findByQrCode("QR-WEG-T1")).thenReturn(Optional.of(equipment));
        when(equipmentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(equipment)));
    }

    @Test
    void technicianCanReadEquipment() throws Exception {
        mockMvc.perform(get("/api/v1/equipment/{id}", equipmentId)
                        .with(user("tech").roles("TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Transformador T1"))
                .andExpect(jsonPath("$.qrCode").value("QR-WEG-T1"));
    }

    @Test
    void technicianCanLookupEquipmentByQrCode() throws Exception {
        mockMvc.perform(get("/api/v1/equipment/by-qr/{qrCode}", "QR-WEG-T1")
                        .with(user("tech").roles("TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Transformador T1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void technicianGetsForbiddenFromCreateEquipment() throws Exception {
        String payload = """
                {
                  "siteId": "%s",
                  "name": "Bomba de Agua",
                  "qrCode": "QR-BOMBA-01"
                }
                """.formatted(siteId);

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(user("tech").roles("TECHNICIAN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void supervisorCanCreateEquipment() throws Exception {
        when(equipmentRepository.saveAndFlush(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "siteId": "%s",
                  "name": "Bomba de Agua",
                  "qrCode": "QR-BOMBA-01"
                }
                """.formatted(siteId);

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bomba de Agua"))
                .andExpect(jsonPath("$.qrCode").value("QR-BOMBA-01"));
    }
}
