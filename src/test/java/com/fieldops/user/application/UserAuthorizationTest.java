package com.fieldops.user.application;

import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class UserAuthorizationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.create(
                "Maria Silva",
                "maria@example.com",
                "$2a$10$secretHash",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadAndManageUsers() {
        assertThat(userService.findById(userId)).isSameAs(user);

        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User created = userService.createUser(
                "Joao Silva",
                "joao@example.com",
                "StrongPassword",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        );

        assertThat(created.getEmail()).isEqualTo("joao@example.com");
        verify(userRepository).findById(userId);
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisorCanReadButCannotManageUsers() {
        assertThat(userService.findById(userId)).isSameAs(user);

        assertThatThrownBy(() -> userService.createUser(
                "Joao Silva",
                "joao@example.com",
                "StrongPassword",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        )).isInstanceOf(AccessDeniedException.class);

        verify(userRepository).findById(userId);
    }

    @Test
    @WithMockUser(roles = "TECHNICIAN")
    void technicianCannotReadOrManageUsers() {
        assertThatThrownBy(() -> userService.findById(userId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.updateStatus(userId, UserStatus.BLOCKED))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    @WithMockUser(roles = "CLIENT_VIEWER")
    void clientViewerCannotReadOrManageUsers() {
        assertThatThrownBy(() -> userService.findById(userId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    @WithMockUser(roles = "TECHNICIAN")
    void technicianGetsForbiddenFromAdministrativeUserEndpoint() throws Exception {
        mockMvc.perform(get("/users/{id}", userId)
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisorGetsForbiddenFromUserManagementEndpoint() throws Exception {
        mockMvc.perform(post("/users")
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Joao Silva",
                                  "email": "joao@example.com",
                                  "password": "StrongPassword",
                                  "role": "TECHNICIAN",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }
}
