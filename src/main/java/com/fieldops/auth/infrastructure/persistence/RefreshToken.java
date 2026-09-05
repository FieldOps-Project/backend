package com.fieldops.auth.infrastructure.persistence;
import com.fieldops.user.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "ux_refresh_tokens_hash", columnList = "token_hash", unique = true),
        @Index(name = "ix_refresh_tokens_user", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;
    @Column(name = "session_version", nullable = false, updatable = false)
    private int sessionVersion;
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    protected RefreshToken() {
    }
    private RefreshToken(User user, String tokenHash, int sessionVersion, Instant expiresAt, Instant createdAt) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.sessionVersion = sessionVersion;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
    public static RefreshToken create(User user, String tokenHash, int sessionVersion, Instant expiresAt, Instant createdAt) {
        return new RefreshToken(user, tokenHash, sessionVersion, expiresAt, createdAt);
    }
    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }
    public UUID getId() {
        return id;
    }
    public User getUser() {
        return user;
    }
    public String getTokenHash() {
        return tokenHash;
    }
    public int getSessionVersion() {
        return sessionVersion;
    }
    public Instant getExpiresAt() {
        return expiresAt;
    }
    public Instant getRevokedAt() {
        return revokedAt;
    }
}
