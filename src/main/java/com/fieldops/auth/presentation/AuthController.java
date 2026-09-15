package com.fieldops.auth.presentation;

import com.fieldops.auth.presentation.dto.AuthenticatedUserResponse;
import com.fieldops.auth.presentation.dto.LoginRequest;
import com.fieldops.auth.presentation.dto.LoginResponse;
import com.fieldops.auth.presentation.dto.RefreshRequest;
import com.fieldops.auth.presentation.dto.TokenResponse;
import com.fieldops.shared.presentation.dto.ApiError;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication contract endpoints for client integration.")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class AuthController {

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Authenticate with e-mail and password",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(value = """
                                    { "email": "tecnico@fieldops.local", "password": "senha-informada-pelo-usuario" }
                                    """)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Authenticated successfully",
                            content = @Content(
                                    schema = @Schema(implementation = LoginResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "accessToken": "token",
                                              "refreshToken": "token-de-renovacao",
                                              "expiresIn": 900,
                                              "user": {
                                                "id": "8a50e30d-0000-0000-0000-000000000000",
                                                "name": "Carlos Tecnico",
                                                "email": "tecnico@fieldops.local",
                                                "role": "TECHNICIAN"
                                              }
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return mockLoginResponse();
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Refresh authentication tokens",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = RefreshRequest.class),
                            examples = @ExampleObject(value = """
                                    { "refreshToken": "token-de-renovacao" }
                                    """)
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tokens renewed",
                            content = @Content(schema = @Schema(implementation = TokenResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse loginResponse = mockLoginResponse();
        return new TokenResponse(loginResponse.accessToken(), loginResponse.refreshToken(), loginResponse.expiresIn());
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get authenticated user profile",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Authenticated user returned",
                            content = @Content(schema = @Schema(implementation = AuthenticatedUserResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public AuthenticatedUserResponse me() {
        return mockLoginResponse().user();
    }

    private LoginResponse mockLoginResponse() {
        return new LoginResponse(
                "token",
                "token-de-renovacao",
                900,
                new AuthenticatedUserResponse(
                        UUID.fromString("8a50e30d-0000-0000-0000-000000000000"),
                        "Carlos Tecnico",
                        "tecnico@fieldops.local",
                        "TECHNICIAN"
                )
        );
    }
}
