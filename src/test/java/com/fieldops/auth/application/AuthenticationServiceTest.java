package com.fieldops.auth.application;

import com.fieldops.auth.domain.exception.AuthException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository,
                passwordEncoder,
                jwtTokenService,
                refreshTokenService
        );
    }

    @Test
    void wrongPasswordAndUnknownEmailUseTheSamePublicError() {
        User user = user("correct-password", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("maria@example.com"))
                .thenReturn(Optional.of(user));

        AuthException wrongPassword = catchThrowableOfType(
                () -> authenticationService.login("maria@example.com", "wrong-password"),
                AuthException.class
        );

        when(userRepository.findByEmailIgnoreCase("unknown@example.com"))
                .thenReturn(Optional.empty());
        AuthException unknownEmail = catchThrowableOfType(
                () -> authenticationService.login("unknown@example.com", "wrong-password"),
                AuthException.class
        );

        assertThat(wrongPassword.getCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(unknownEmail.getCode()).isEqualTo(wrongPassword.getCode());
        assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    @Test
    void inactiveUserGetsAnActionableAuthenticationError() {
        User user = user("correct-password", UserStatus.BLOCKED);
        when(userRepository.findByEmailIgnoreCase("maria@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationService.login("maria@example.com", "correct-password"))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).getCode())
                .isEqualTo("AUTH_USER_INACTIVE");
    }

    @Test
    void validLoginReturnsAccessAndRefreshTokensAndNeverThePasswordHash() {
        User user = user("correct-password", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("maria@example.com"))
                .thenReturn(Optional.of(user));
        when(jwtTokenService.issueAccessToken(user)).thenReturn("access-token");
        when(jwtTokenService.getAccessTokenExpiresInSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(user))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", user));

        AuthenticationService.LoginSession session = authenticationService.login(
                " MARIA@EXAMPLE.COM ", "correct-password");

        assertThat(session.accessToken()).isEqualTo("access-token");
        assertThat(session.refreshToken()).isEqualTo("refresh-token");
        assertThat(session.expiresIn()).isEqualTo(900L);
        assertThat(session.user().getPasswordHash()).isNotBlank();
        verify(refreshTokenService).issue(user);
    }

    private User user(String rawPassword, UserStatus status) {
        return User.create(
                "Maria Silva",
                "maria@example.com",
                passwordEncoder.encode(rawPassword),
                UserRole.TECHNICIAN,
                status,
                null
        );
    }
}
