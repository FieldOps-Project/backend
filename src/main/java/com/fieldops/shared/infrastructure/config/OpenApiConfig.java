package com.fieldops.shared.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI fieldOpsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FieldOps API")
                        .version("v1")
                        .description("Initial versioned contract for Sprint 1 integrations."))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://api-hml.fieldops.com.br").description("Integracao"),
                        new Server().url("https://api.fieldops.com.br").description("Producao")
                ));
    }
}
