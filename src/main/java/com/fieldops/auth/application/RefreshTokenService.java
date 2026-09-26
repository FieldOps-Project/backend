package com.fieldops.auth.application;
import com.fieldops.auth.domain.exception.AuthException;
import com.fieldops.auth.infrastructure.persistence.RefreshToken;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenTtl;
    private final Clock clock = Clock.systemUTC();
    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.security.jwt.refresh-token-ttl:P30D}") Duration refreshTokenTtl
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = refreshTokenTtl;
    }
    @Transactional
    public IssuedRefreshToken issue(User user) {
        String rawToken = generateToken();
        Instant now = clock.instant();
        RefreshToken refreshToken = RefreshToken.create(
                user,
                hash(rawToken),
                user.getSessionVersion(),
                now.plus(refreshTokenTtl),
                now
        );
        refreshTokenRepository.save(refreshToken);
        return new IssuedRefreshToken(rawToken, user);
    }
    @Transactional
    public IssuedRefreshToken rotate(String rawToken) {
        RefreshToken current = find(rawToken);
        Instant now = clock.instant();
        User user = current.getUser();
        if (!current.isUsableAt(now) || current.getSessionVersion() != user.getSessionVersion()) {
            throw AuthException.invalidRefreshToken();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthException.inactiveUser();
        }
        current.revoke(now);
        refreshTokenRepository.save(current);
        return issue(user);
    }
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.revoke(clock.instant());
            refreshTokenRepository.save(token);
        });
    }
    private RefreshToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw AuthException.invalidRefreshToken();
        }
        return refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(AuthException::invalidRefreshToken);
    }
    private String generateToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
    public record IssuedRefreshToken(String value, User user) {
    }
}
