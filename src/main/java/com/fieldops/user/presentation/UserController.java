package com.fieldops.user.presentation;

import com.fieldops.shared.presentation.dto.ApiError;
import com.fieldops.user.application.UserService;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.presentation.dto.CreateUserRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/users")
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

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id", description = "Returns user data without password hash.")
    public UserResponse findById(@PathVariable UUID id) {
        return userMapper.toResponse(userService.findById(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update user status", description = "Changes status while preserving the user row history.")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return userMapper.toResponse(userService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inactivate a user", description = "Logical delete: marks the user as INACTIVE and never deletes the row.")
    public void inactivate(@PathVariable UUID id) {
        userService.inactivate(id);
    }
}
