package com.fieldops.auth.application;

import com.fieldops.auth.domain.exception.AuthException;
import com.fieldops.auth.infrastructure.persistence.RefreshToken;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void rotateRevokesThePresentedTokenAndIssuesAReplacement() {
        User user = user(UserStatus.ACTIVE);
        Instant now = Instant.now();
        RefreshToken current = RefreshToken.create(
                user,
                "stored-hash",
                user.getSessionVersion(),
                now.plus(Duration.ofDays(1)),
                now
        );
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(current));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30));
        RefreshTokenService.IssuedRefreshToken replacement = service.rotate("presented-token");

        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(replacement.value()).isNotEqualTo("presented-token");
        assertThat(replacement.value()).hasSize(64);
        verify(refreshTokenRepository).save(current);
    }

    @Test
    void revokedTokenCannotBeRotatedAgain() {
        User user = user(UserStatus.ACTIVE);
        Instant now = Instant.now();
        RefreshToken current = RefreshToken.create(
                user,
                "stored-hash",
                user.getSessionVersion(),
                now.plus(Duration.ofDays(1)),
                now
        );
        current.revoke(now);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(current));

        RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, Duration.ofDays(30));

        assertThatThrownBy(() -> service.rotate("presented-token"))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).getCode())
                .isEqualTo("AUTH_INVALID_REFRESH_TOKEN");
    }

    private User user(UserStatus status) {
        return User.create(
                "Maria Silva",
                "maria@example.com",
                "hashed-password",
                UserRole.TECHNICIAN,
                status,
                null
        );
    }
}
