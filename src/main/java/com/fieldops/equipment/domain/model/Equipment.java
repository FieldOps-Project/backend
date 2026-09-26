package com.fieldops.equipment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "equipment")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(nullable = false)
    private String name;

    @Column(name = "asset_number")
    private String assetNumber;

    @Column(name = "serial_number")
    private String serialNumber;

    private String manufacturer;

    private String model;

    @Column(length = 1000)
    private String description;

    @Column(name = "qr_code", nullable = false, unique = true)
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentStatus status;

    @Column(name = "installed_at")
    private LocalDate installedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Integer version;

    public static Equipment create(
            UUID siteId,
            String name,
            String assetNumber,
            String serialNumber,
            String manufacturer,
            String model,
            String description,
            String qrCode,
            EquipmentStatus status,
            LocalDate installedAt
    ) {
        Equipment equipment = new Equipment();
        equipment.siteId = Objects.requireNonNull(siteId, "siteId must not be null");
        equipment.name = Objects.requireNonNull(name, "name must not be null").trim();
        equipment.assetNumber = trimToNull(assetNumber);
        equipment.serialNumber = trimToNull(serialNumber);
        equipment.manufacturer = trimToNull(manufacturer);
        equipment.model = trimToNull(model);
        equipment.description = trimToNull(description);
        equipment.qrCode = Objects.requireNonNull(qrCode, "qrCode must not be null").trim();
        equipment.status = status != null ? status : EquipmentStatus.ACTIVE;
        equipment.installedAt = installedAt;
        return equipment;
    }

    public void update(
            String name,
            String assetNumber,
            String serialNumber,
            String manufacturer,
            String model,
            String description,
            String qrCode,
            LocalDate installedAt
    ) {
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.assetNumber = trimToNull(assetNumber);
        this.serialNumber = trimToNull(serialNumber);
        this.manufacturer = trimToNull(manufacturer);
        this.model = trimToNull(model);
        this.description = trimToNull(description);
        this.qrCode = Objects.requireNonNull(qrCode, "qrCode must not be null").trim();
        this.installedAt = installedAt;
    }

    public void updateStatus(EquipmentStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "status must not be null");
    }

    private static String trimToNull(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
