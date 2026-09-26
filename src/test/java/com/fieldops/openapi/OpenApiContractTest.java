package com.fieldops.openapi;

import com.fieldops.shared.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {

    @Test
    @DisplayName("OpenAPI docs expose initial auth and inspection contract")
    void apiDocs_includeRequiredContractEndpointsAndSchemas() {
        OpenAPI openAPI = new OpenApiConfig().fieldOpsOpenAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("FieldOps API");
        assertThat(openAPI.getPaths()).containsKeys(
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/me",
                "/api/v1/inspections",
                "/api/v1/mobile/inspections");
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(openAPI.getComponents().getSchemas()).containsKey("ApiError");
    }
}
