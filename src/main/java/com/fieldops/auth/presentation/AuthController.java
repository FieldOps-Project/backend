package com.fieldops.auth.presentation;
import com.fieldops.auth.application.AuthenticationService;
import com.fieldops.auth.presentation.dto.AuthUserResponse;
import com.fieldops.auth.presentation.dto.LoginRequest;
import com.fieldops.auth.presentation.dto.LoginResponse;
import com.fieldops.auth.presentation.dto.RefreshTokenRequest;
import com.fieldops.auth.presentation.dto.TokenResponse;
import com.fieldops.auth.presentation.mapper.AuthUserMapper;
import com.fieldops.user.domain.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "JWT authentication and session management.")
public class AuthController {
    private final AuthenticationService authenticationService;
    private final AuthUserMapper authUserMapper;
    public AuthController(AuthenticationService authenticationService, AuthUserMapper authUserMapper) {
        this.authenticationService = authenticationService;
        this.authUserMapper = authUserMapper;
    }
    @PostMapping("/login")
    @Operation(
            summary = "Authenticate a user",
            responses = @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials or inactive user",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ApiError"))
            )
    )
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.LoginSession session = authenticationService.login(request.email(), request.password());
        return new LoginResponse(
                session.accessToken(),
                session.refreshToken(),
                session.expiresIn(),
                authUserMapper.toResponse(session.user())
        );
    }
    @PostMapping("/refresh")
    @Operation(summary = "Rotate the access and refresh tokens")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthenticationService.TokenSession session = authenticationService.refresh(request.refreshToken());
        return new TokenResponse(session.accessToken(), session.refreshToken(), session.expiresIn());
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.logout(request.refreshToken());
    }
    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user's public profile")
    public AuthUserResponse me(Authentication authentication) {
        return authUserMapper.toResponse((User) authentication.getPrincipal());
    }
}
