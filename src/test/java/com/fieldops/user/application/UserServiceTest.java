package com.fieldops.user.application;

import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.user.domain.exception.EmailAlreadyExistsException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;

    @Test
    void createUser_hashesPasswordAndNormalizesEmailBeforePersisting() {
        userService = new UserService(userRepository, passwordEncoder);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.createUser(
                "Maria Silva",
                " Maria.Silva@Example.COM ",
                "plain-password",
                UserRole.TECHNICIAN,
                null,
                " 11999990000 "
        );

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());

        User persisted = userCaptor.getValue();
        assertThat(persisted.getEmail()).isEqualTo("maria.silva@example.com");
        assertThat(persisted.getPasswordHash()).isNotEqualTo("plain-password");
        assertThat(passwordEncoder.matches("plain-password", persisted.getPasswordHash())).isTrue();
        assertThat(persisted.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void createUser_translatesUniqueEmailViolationToConflictCode() {
        userService = new UserService(userRepository, passwordEncoder);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("ux_users_email"));

        assertThatThrownBy(() -> userService.createUser(
                "Maria Silva",
                "maria@example.com",
                "plain-password",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        ))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("A user with this email already exists");
    }

    @Test
    void createUser_doesNotTranslateUnrelatedIntegrityViolation() {
        userService = new UserService(userRepository, passwordEncoder);
        DataIntegrityViolationException violation = new DataIntegrityViolationException("users_role_check");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(violation);

        assertThatThrownBy(() -> userService.createUser(
                "Maria Silva",
                "maria@example.com",
                "plain-password",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        )).isSameAs(violation);
    }

    @Test
    void inactivate_marksUserInactiveWithoutPhysicalDelete() {
        userService = new UserService(userRepository, passwordEncoder);
        UUID userId = UUID.randomUUID();
        User user = User.create(
                "Maria Silva",
                "maria@example.com",
                "$2a$10$hashedpasswordvalue",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.inactivate(userId);

        assertThat(updated.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(userRepository).save(user);
        verify(userRepository, never()).delete(any(User.class));
        verify(userRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void findById_throwsNotFoundWhenUserDoesNotExist() {
        userService = new UserService(userRepository, passwordEncoder);
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }
}
