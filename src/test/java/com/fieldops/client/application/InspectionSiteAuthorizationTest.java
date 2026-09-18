package com.fieldops.client.application;

import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class InspectionSiteAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InspectionSiteRepository inspectionSiteRepository;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private UUID clientId;
    private UUID siteId;
    private Client client;
    private InspectionSite site;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        client = Client.create("Industria Alfa", null, null, null, null, ClientStatus.ACTIVE);
        site = InspectionSite.create(
                client,
                "Unidade Sorocaba",
                "Fabrica principal",
                "Av. das Industrias, 1000",
                "Sorocaba",
                "SP",
                "18000-000",
                null,
                null,
                "Joao da Silva",
                "+55 15 99999-0000",
                InspectionSiteStatus.ACTIVE
        );
        when(inspectionSiteRepository.findById(siteId)).thenReturn(Optional.of(site));
    }

    @Test
    void technicianGetsForbiddenFromSiteManagementEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/sites/{id}", siteId)
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void supervisorCanCreateSiteForExistingClient() throws Exception {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(inspectionSiteRepository.saveAndFlush(any(InspectionSite.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/sites")
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "%s",
                                  "name": "Unidade Sorocaba",
                                  "city": "Sorocaba",
                                  "state": "SP",
                                  "latitude": -23.5015,
                                  "longitude": -47.4526
                                }
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Unidade Sorocaba"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void creatingSiteForUnknownClientReturnsNotFound() throws Exception {
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/sites")
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "%s",
                                  "name": "Unidade Sorocaba"
                                }
                                """.formatted(clientId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));

        verify(inspectionSiteRepository, never()).saveAndFlush(any(InspectionSite.class));
    }

    @Test
    void creatingSiteWithoutClientReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sites")
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unidade Sorocaba\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("clientId"));
    }

    @Test
    void nestedEndpointListsOnlySitesForRequestedClient() throws Exception {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(inspectionSiteRepository.findAll(
                any(Specification.class), any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(site), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/clients/{clientId}/sites", clientId)
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Unidade Sorocaba"))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void inactiveSiteIsStillReadableButDeleteIsNotExposed() throws Exception {
        site.updateStatus(InspectionSiteStatus.INACTIVE);

        mockMvc.perform(get("/api/v1/sites/{id}", siteId)
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(delete("/api/v1/sites/{id}", siteId)
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void incompleteCoordinatesReturnCanonicalBadRequest() throws Exception {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        mockMvc.perform(post("/api/v1/sites")
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "%s",
                                  "name": "Unidade Sorocaba",
                                  "latitude": -23.5015
                                }
                                """.formatted(clientId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
    }
}
