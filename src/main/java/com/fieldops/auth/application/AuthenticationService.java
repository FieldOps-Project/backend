package com.fieldops.auth.application;
import com.fieldops.auth.domain.exception.AuthException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
@Service
public class AuthenticationService {
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }
    @Transactional
    public LoginSession login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, DUMMY_PASSWORD_HASH);
            throw AuthException.invalidCredentials();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw AuthException.invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthException.inactiveUser();
        }
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);
        return new LoginSession(
                user,
                jwtTokenService.issueAccessToken(user),
                refreshToken.value(),
                jwtTokenService.getAccessTokenExpiresInSeconds()
        );
    }
    @Transactional
    public TokenSession refresh(String refreshToken) {
        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
        return new TokenSession(
                jwtTokenService.issueAccessToken(rotated.user()),
                rotated.value(),
                jwtTokenService.getAccessTokenExpiresInSeconds()
        );
    }
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }
    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw AuthException.invalidCredentials();
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
    public record LoginSession(User user, String accessToken, String refreshToken, long expiresIn) {
    }
    public record TokenSession(String accessToken, String refreshToken, long expiresIn) {
    }
}
