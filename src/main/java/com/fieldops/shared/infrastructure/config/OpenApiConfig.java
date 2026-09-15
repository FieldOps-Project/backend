package com.fieldops.shared.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String API_ERROR = "ApiError";

    @Bean
    OpenAPI fieldOpsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FieldOps API")
                        .version("v1")
                        .description("Contrato inicial da API FieldOps para mobile e painel.")
                        .contact(new Contact().name("FieldOps Project")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://api.fieldops.com.br").description("Production")))
                .tags(List.of(
                        new Tag().name("Authentication").description("Authentication and session contract."),
                        new Tag().name("Inspections").description("Inspection listing contract.")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSchemas(API_ERROR, apiErrorSchema())
                        .addSchemas("LoginRequest", loginRequestSchema())
                        .addSchemas("TokenRequest", tokenRequestSchema())
                        .addSchemas("LoginResponse", loginResponseSchema())
                        .addSchemas("TokenResponse", tokenResponseSchema())
                        .addSchemas("AuthUser", authUserSchema())
                        .addSchemas("InspectionPage", inspectionPageSchema()))
                .paths(contractPaths());
    }

    private Paths contractPaths() {
        return new Paths()
                .addPathItem("/api/v1/auth/login", new PathItem().post(loginOperation()))
                .addPathItem("/api/v1/auth/refresh", new PathItem().post(refreshOperation()))
                .addPathItem("/api/v1/auth/me", new PathItem().get(meOperation()))
                .addPathItem("/api/v1/inspections", new PathItem().get(inspectionsOperation()))
                .addPathItem("/api/v1/mobile/inspections", new PathItem().get(mobileInspectionsOperation()));
    }

    private Operation loginOperation() {
        return new Operation()
                .tags(List.of("Authentication"))
                .summary("Authenticate a user")
                .description("Authenticates credentials and starts a session.")
                .requestBody(jsonBody("LoginRequest", Map.of(
                        "email", "tecnico@fieldops.local",
                        "password", "senha-informada-pelo-usuario")))
                .responses(new ApiResponses()
                        .addApiResponse("200", jsonResponse("LoginResponse", "Successful authentication"))
                        .addApiResponse("400", errorResponse("Invalid payload"))
                        .addApiResponse("401", errorResponse("Invalid credentials")));
    }

    private Operation refreshOperation() {
        return new Operation()
                .tags(List.of("Authentication"))
                .summary("Refresh an access token")
                .requestBody(jsonBody("TokenRequest", Map.of("refreshToken", "token-de-renovacao")))
                .responses(new ApiResponses()
                        .addApiResponse("200", jsonResponse("TokenResponse", "Tokens renewed"))
                        .addApiResponse("400", errorResponse("Invalid payload"))
                        .addApiResponse("401", errorResponse("Invalid or expired refresh token")));
    }

    private Operation meOperation() {
        return securedOperation("Authentication", "Get the current authenticated user")
                .responses(new ApiResponses()
                        .addApiResponse("200", jsonResponse("AuthUser", "Current user"))
                        .addApiResponse("401", errorResponse("Authentication required")));
    }

    private Operation inspectionsOperation() {
        return securedOperation("Inspections", "List inspections for the panel")
                .addParametersItem(pageParameter())
                .responses(new ApiResponses()
                        .addApiResponse("200", jsonResponse("InspectionPage", "Inspection page"))
                        .addApiResponse("401", errorResponse("Authentication required")));
    }

    private Operation mobileInspectionsOperation() {
        return securedOperation("Inspections", "List inspections available to mobile clients")
                .addParametersItem(pageParameter())
                .responses(new ApiResponses()
                        .addApiResponse("200", jsonResponse("InspectionPage", "Inspection page"))
                        .addApiResponse("401", errorResponse("Authentication required")));
    }

    private Operation securedOperation(String tag, String summary) {
        return new Operation()
                .tags(List.of(tag))
                .summary(summary)
                .security(List.of(new SecurityRequirement().addList("bearerAuth")));
    }

    private Parameter pageParameter() {
        return new Parameter()
                .in("query")
                .name("page")
                .description("Zero-based page number")
                .schema(new IntegerSchema()._default(0).minimum(BigDecimal.ZERO));
    }

        private RequestBody jsonBody(String schemaName, Object example) {
        return new RequestBody()
                .required(true)
                .content(new Content().addMediaType("application/json", new io.swagger.v3.oas.models.media.MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/" + schemaName))
                        .example(example)));
    }

    private ApiResponse jsonResponse(String schemaName, String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new io.swagger.v3.oas.models.media.MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/" + schemaName))));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new io.swagger.v3.oas.models.media.MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/" + API_ERROR))));
    }

    private Schema<?> apiErrorSchema() {
        return new ObjectSchema()
                .description("Canonical error envelope returned by the API.")
                .required(List.of("timestamp", "status", "code", "message", "path", "requestId", "fieldErrors"))
                .addProperties("timestamp", new StringSchema().format("date-time"))
                .addProperties("status", new IntegerSchema().example(400))
                .addProperties("code", new StringSchema().example("VALIDATION_ERROR"))
                .addProperties("message", new StringSchema().example("Request validation failed"))
                .addProperties("path", new StringSchema().example("/api/v1/auth/login"))
                .addProperties("requestId", new StringSchema().example("8e5d8e75-821a-4153-9bf5-b30011ac3975"))
                .addProperties("fieldErrors", new ArraySchema().items(new ObjectSchema()
                        .addProperties("field", new StringSchema().example("email"))
                        .addProperties("message", new StringSchema().example("must be a well-formed email address"))));
    }

    private Schema<?> loginRequestSchema() {
        return new ObjectSchema()
                .required(List.of("email", "password"))
                .addProperties("email", new StringSchema().format("email").example("tecnico@fieldops.local"))
                .addProperties("password", new StringSchema().format("password").example("senha-informada-pelo-usuario"));
    }

        private Schema<?> tokenRequestSchema() {
                return new ObjectSchema()
                                .required(List.of("refreshToken"))
                                .addProperties("refreshToken", new StringSchema().example("token-de-renovacao"));
        }

    private Schema<?> loginResponseSchema() {
        return new ObjectSchema()
                .addProperties("accessToken", new StringSchema().example("token"))
                .addProperties("refreshToken", new StringSchema().example("token-de-renovacao"))
                .addProperties("expiresIn", new IntegerSchema().format("int64").example(900))
                .addProperties("user", new Schema<>().$ref("#/components/schemas/AuthUser"));
    }

    private Schema<?> tokenResponseSchema() {
        return new ObjectSchema()
                .addProperties("accessToken", new StringSchema().example("token"))
                .addProperties("refreshToken", new StringSchema().example("token-de-renovacao"))
                .addProperties("expiresIn", new IntegerSchema().format("int64").example(900));
    }

    private Schema<?> authUserSchema() {
        return new ObjectSchema()
                .addProperties("id", new StringSchema().format("uuid").example("8a50e30d-0000-4000-8000-000000000001"))
                .addProperties("name", new StringSchema().example("Carlos Tecnico"))
                .addProperties("email", new StringSchema().format("email").example("tecnico@fieldops.local"))
                .addProperties("role", new StringSchema()._enum(List.of("ADMIN", "SUPERVISOR", "TECHNICIAN", "CLIENT_VIEWER")).example("TECHNICIAN"));
    }

    private Schema<?> inspectionPageSchema() {
        return new ObjectSchema()
                .addProperties("content", new ArraySchema().items(new ObjectSchema()
                        .addProperties("id", new StringSchema().format("uuid"))
                        .addProperties("status", new StringSchema().example("PENDING"))
                        .addProperties("scheduledAt", new StringSchema().format("date-time"))))
                .addProperties("page", new IntegerSchema().example(0))
                .addProperties("size", new IntegerSchema().example(20))
                .addProperties("totalElements", new IntegerSchema().format("int64").example(1))
                .addProperties("totalPages", new IntegerSchema().example(1))
                .addProperties("first", new Schema<Boolean>().type("boolean").example(true))
                .addProperties("last", new Schema<Boolean>().type("boolean").example(true));
    }
}