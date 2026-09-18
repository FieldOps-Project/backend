package com.fieldops.client.domain.model;

import com.fieldops.shared.domain.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inspection_sites")
@EntityListeners(AuditingEntityListener.class)
public class InspectionSite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "client_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inspection_sites_client")
    )
    private Client client;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InspectionSiteStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected InspectionSite() {
    }

    private InspectionSite(
            Client client,
            String name,
            String description,
            String addressLine,
            String city,
            String state,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String contactName,
            String contactPhone,
            InspectionSiteStatus status
    ) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.name = requireText(name, "name");
        this.description = normalizeOptional(description);
        this.addressLine = normalizeOptional(addressLine);
        this.city = normalizeOptional(city);
        this.state = normalizeOptional(state);
        this.postalCode = normalizeOptional(postalCode);
        this.latitude = latitude;
        this.longitude = longitude;
        this.contactName = normalizeOptional(contactName);
        this.contactPhone = normalizeOptional(contactPhone);
        this.status = status == null ? InspectionSiteStatus.ACTIVE : status;
    }

    public static InspectionSite create(
            Client client,
            String name,
            String description,
            String addressLine,
            String city,
            String state,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String contactName,
            String contactPhone,
            InspectionSiteStatus status
    ) {
        return new InspectionSite(
                client,
                name,
                description,
                addressLine,
                city,
                state,
                postalCode,
                latitude,
                longitude,
                contactName,
                contactPhone,
                status
        );
    }

    public void updateProfile(
            String name,
            String description,
            String addressLine,
            String city,
            String state,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String contactName,
            String contactPhone
    ) {
        this.name = requireText(name, "name");
        this.description = normalizeOptional(description);
        this.addressLine = normalizeOptional(addressLine);
        this.city = normalizeOptional(city);
        this.state = normalizeOptional(state);
        this.postalCode = normalizeOptional(postalCode);
        this.latitude = latitude;
        this.longitude = longitude;
        this.contactName = normalizeOptional(contactName);
        this.contactPhone = normalizeOptional(contactPhone);
    }

    public void updateStatus(InspectionSiteStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public boolean isActive() {
        return status == InspectionSiteStatus.ACTIVE;
    }

    public void ensureAvailableForScheduling() {
        if (!isActive()) {
            throw new BusinessRuleException(
                    "INSPECTION_SITE_INACTIVE",
                    "Inactive inspection sites cannot be selected for new inspections"
            );
        }
    }

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = InspectionSiteStatus.ACTIVE;
        }
    }

    public UUID getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public InspectionSiteStatus getStatus() {
        return status;
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
