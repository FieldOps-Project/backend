package com.fieldops.user.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.shared.presentation.dto.PageResponse;
import com.fieldops.shared.presentation.dto.ApiError;
import com.fieldops.user.application.UserService;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.presentation.dto.CreateUserRequest;
import com.fieldops.user.presentation.dto.ResetPasswordResponse;
import com.fieldops.user.presentation.dto.UpdateUserRequest;
import com.fieldops.user.presentation.dto.UpdateUserStatusRequest;
import com.fieldops.user.presentation.dto.UserResponse;
import com.fieldops.user.presentation.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/users", "/users"})
@Tag(name = "Users", description = "User management endpoints.")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    @Operation(
            summary = "Create a user",
            description = "Stores the password using BCrypt and never returns the password hash.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "User created"),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid payload",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "E-mail already exists",
                            content = @Content(
                                    schema = @Schema(implementation = ApiError.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "status": 409,
                                              "code": "EMAIL_ALREADY_EXISTS",
                                              "message": "A user with this email already exists"
                                            }
                                            """)
                            )
                    )
            }
    )
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(
                request.name(),
                request.email(),
                request.password(),
                request.role(),
                request.status(),
                request.phone()
        );
        return userMapper.toResponse(user);
    }

    @GetMapping
    @PreAuthorize(AuthorizationPolicies.USERS_READ)
    @Operation(summary = "List users", description = "Lists users with optional filters and bounded pagination.")
    public PageResponse<UserResponse> findAll(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return PageResponse.from(userService.findAll(name, email, role, status, page, size, sort)
                .map(userMapper::toResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AuthorizationPolicies.USERS_READ)
    @Operation(summary = "Get a user by id", description = "Returns user data without password hash.")
    public UserResponse findById(@PathVariable UUID id) {
        return userMapper.toResponse(userService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    @Operation(summary = "Update a user", description = "Updates profile fields without accepting a password hash.")
    public UserResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userMapper.toResponse(userService.updateUser(
                id,
                request.name(),
                request.email(),
                request.role(),
                request.phone()
        ));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    @Operation(summary = "Update user status", description = "Changes status while preserving the user row history.")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return userMapper.toResponse(userService.updateStatus(id, request.status()));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    @Operation(summary = "Reset a user's password", description = "Generates a temporary password returned only by this response.")
    public ResetPasswordResponse resetPassword(@PathVariable UUID id) {
        return new ResetPasswordResponse(userService.resetPassword(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    @Operation(summary = "Inactivate a user", description = "Logical delete: marks the user as INACTIVE and never deletes the row.")
    public void inactivate(@PathVariable UUID id) {
        userService.inactivate(id);
    }
}
