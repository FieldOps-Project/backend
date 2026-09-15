package com.fieldops.shared.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {

    @Test
    void declaresTheInitialContractAndReusableErrorSchema() {
        OpenAPI openAPI = new OpenApiConfig().fieldOpsOpenAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("FieldOps API");
        assertThat(openAPI.getServers()).hasSize(2);
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(openAPI.getComponents().getSchemas()).containsKey("ApiError");
        assertThat(openAPI.getPaths()).containsKeys(
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/me",
                "/api/v1/inspections",
                "/api/v1/mobile/inspections");
        assertThat(openAPI.getPaths().get("/api/v1/auth/login").getPost().getResponses())
                .containsKeys("400", "401");
        assertThat(openAPI.getPaths().get("/api/v1/inspections").getGet().getSecurity())
                .extracting(requirement -> requirement.keySet())
                .contains(java.util.Set.of("bearerAuth"));
    }
}