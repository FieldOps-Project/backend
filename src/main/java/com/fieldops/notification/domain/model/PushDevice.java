package com.fieldops.notification.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "push_devices", indexes = {
        @Index(name = "ix_push_devices_user", columnList = "user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "ux_push_devices_user_device", columnNames = {"user_id", "device_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class PushDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "push_token", nullable = false, length = 512)
    private String pushToken;

    @Column(name = "platform", nullable = false, length = 32)
    private String platform;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected PushDevice() {
    }

    private PushDevice(UUID userId, String deviceId, String pushToken, String platform) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.deviceId = requireText(deviceId, "deviceId");
        this.pushToken = requireText(pushToken, "pushToken");
        this.platform = requireText(platform, "platform");
    }

    public static PushDevice create(UUID userId, String deviceId, String pushToken, String platform) {
        return new PushDevice(userId, deviceId, pushToken, platform);
    }

    public void updateRegistration(String pushToken, String platform) {
        this.pushToken = requireText(pushToken, "pushToken");
        this.platform = requireText(platform, "platform");
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getPushToken() {
        return pushToken;
    }

    public String getPlatform() {
        return platform;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
