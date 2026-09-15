package com.fieldops.openapi;

import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class OpenApiContractTest {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    @DisplayName("OpenAPI docs expose initial auth and inspection contract")
    void apiDocs_includeRequiredContractEndpointsAndSchemas() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/inspections'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/mobile/inspections'].get").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.ApiError").exists())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"$ref\":\"#/components/schemas/ApiError\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"email\":\"tecnico@fieldops.local\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"password\":\"senha-informada-pelo-usuario\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"accessToken\":\"token\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"refreshToken\":\"token-de-renovacao\"")));
    }
}
