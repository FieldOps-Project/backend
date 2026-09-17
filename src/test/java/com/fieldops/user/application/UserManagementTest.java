package com.fieldops.user.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.user.domain.exception.EmailAlreadyExistsException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditEventService auditEventService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, auditEventService);
    }

    @Test
    void findAllAppliesFiltersAndClampsPageSize() {
        User user = User.create(
                "Maria Silva",
                "maria@example.com",
                passwordEncoder.encode("password"),
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        );
        Page<User> expected = new PageImpl<>(List.of(user));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(expected);

        Page<User> result = userService.findAll(
                "Maria",
                "EXAMPLE.COM",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                2,
                500,
                "name,asc"
        );

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("name").isAscending()).isTrue();
    }

    @Test
    void findAllRejectsSortFieldsOutsideTheAllowList() {
        assertThatThrownBy(() -> userService.findAll(null, null, null, null, 0, 20, "passwordHash,asc"))
                .isInstanceOf(com.fieldops.shared.domain.exception.InvalidRequestException.class)
                .hasMessageContaining("allowed user field");

        verify(userRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void updateUserNormalizesEmailAndDoesNotChangeThePassword() throws Exception {
        UUID id = UUID.randomUUID();
        User user = user("old@example.com", "old-password", UserRole.TECHNICIAN);
        setId(user, id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        User updated = userService.updateUser(
                id,
                " Maria Silva ",
                " NEW@EXAMPLE.COM ",
                UserRole.SUPERVISOR,
                " 11999990000 "
        );

        assertThat(updated.getName()).isEqualTo("Maria Silva");
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.getRole()).isEqualTo(UserRole.SUPERVISOR);
        assertThat(updated.getPhone()).isEqualTo("11999990000");
        assertThat(passwordEncoder.matches("old-password", updated.getPasswordHash())).isTrue();
    }

    @Test
    void updateUserRejectsEmailAlreadyOwnedByAnotherUser() throws Exception {
        UUID id = UUID.randomUUID();
        User user = user("old@example.com", "old-password", UserRole.TECHNICIAN);
        User duplicate = user("new@example.com", "other-password", UserRole.ADMIN);
        setId(user, id);
        setId(duplicate, UUID.randomUUID());
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.of(duplicate));

        assertThatThrownBy(() -> userService.updateUser(
                id, "Maria Silva", "new@example.com", UserRole.TECHNICIAN, null
        )).isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void resetPasswordStoresOnlyTheHashAndInvalidatesTheSession() throws Exception {
        UUID id = UUID.randomUUID();
        User user = user("maria@example.com", "old-password", UserRole.TECHNICIAN);
        setId(user, id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        String temporaryPassword = userService.resetPassword(id);

        assertThat(temporaryPassword).hasSize(20);
        assertThat(user.getPasswordHash()).isNotEqualTo(temporaryPassword);
        assertThat(passwordEncoder.matches(temporaryPassword, user.getPasswordHash())).isTrue();
        assertThat(user.getSessionVersion()).isEqualTo(1);
    }

    @Test
    void statusChangeRecordsTheAuthenticatedOperationWithoutSensitiveValues() throws Exception {
        UUID id = UUID.randomUUID();
        User user = user("maria@example.com", "old-password", UserRole.TECHNICIAN);
        setId(user, id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateStatus(id, UserStatus.BLOCKED);

        verify(auditEventService).recordUserEvent(
                eq("USER_STATUS_CHANGED"),
                eq(id),
                contains("status=ACTIVE"),
                contains("status=BLOCKED")
        );
    }

    private User user(String email, String password, UserRole role) {
        return User.create(
                "Maria Silva",
                email,
                passwordEncoder.encode(password),
                role,
                UserStatus.ACTIVE,
                null
        );
    }

    private void setId(User user, UUID id) throws Exception {
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}
