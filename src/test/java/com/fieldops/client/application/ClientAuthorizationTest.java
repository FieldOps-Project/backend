package com.fieldops.client.application;

import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
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
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
class ClientAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private InspectionSiteRepository inspectionSiteRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private UUID clientId;
    private Client client;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        client = Client.create(
                "Industria Alfa",
                "Industria Alfa Ltda",
                "12345678000190",
                "contato@industria-alfa.com.br",
                "+55 11 99999-0000",
                ClientStatus.ACTIVE
        );
        when(clientRepository.findById(clientId)).thenReturn(java.util.Optional.of(client));
    }

    @Test
    void technicianGetsForbiddenFromClientManagementEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}", clientId)
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void supervisorCanReadClients() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}", clientId)
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Industria Alfa"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.document").value("12345678000190"));
    }

    @Test
    void supervisorCanCreateClient() throws Exception {
        when(clientRepository.saveAndFlush(any(Client.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/clients")
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Industria Beta",
                                  "legalName": "Industria Beta Ltda",
                                  "document": "12345678901",
                                  "email": "contato@industria-beta.com.br",
                                  "phone": "+55 11 98888-0000"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Industria Beta"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void invalidClientDocumentReturnsCanonicalBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Industria Beta",
                                  "document": "invalid-document"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("document"));
    }

    @Test
    void invalidStatusFilterReturnsCanonicalBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/clients")
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void clientContextDoesNotExposePhysicalDeleteEndpoint() throws Exception {
        mockMvc.perform(delete("/api/v1/clients/{id}", clientId)
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listEndpointUsesStandardPageEnvelope() throws Exception {
        when(clientRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(client), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/clients")
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .param("search", "alfa")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }
}
