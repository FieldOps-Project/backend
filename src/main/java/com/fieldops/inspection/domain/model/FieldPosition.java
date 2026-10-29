package com.fieldops.inspection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * A position read once by the technician's device, at the start or at the
 * completion of an inspection (RN-060 to RN-062).
 *
 * <p>It says where the device was at that moment. It is not the registered
 * address of the site nor the position of the equipment, which are separate
 * concepts. A missing position is {@code null} on the inspection: a
 * coordinate is never invented or approximated.</p>
 *
 * <p>Column names are overridden by each use in {@link Inspection}.</p>
 */
@Embeddable
public class FieldPosition {

    private static final int COORDINATE_SCALE = 6;
    private static final int ACCURACY_SCALE = 2;
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    /** Radius in meters reported by the device; poor accuracy is information, not an error. */
    @Column(name = "accuracy_meters", precision = 10, scale = 2)
    private BigDecimal accuracyMeters;

    /** Device clock when the position was read. */
    @Column(name = "captured_at")
    private Instant capturedAtDevice;

    protected FieldPosition() {
    }

    /**
     * Validates the reading. Coordinates are kept to six decimal places
     * (about 11 cm), the same precision used for registered sites.
     */
    public static FieldPosition of(
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyMeters,
            Instant capturedAtDevice
    ) {
        Objects.requireNonNull(latitude, "latitude must not be null");
        Objects.requireNonNull(longitude, "longitude must not be null");
        if (latitude.abs().compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (longitude.abs().compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
        if (accuracyMeters != null && accuracyMeters.signum() < 0) {
            throw new IllegalArgumentException("accuracyMeters must not be negative");
        }
        FieldPosition position = new FieldPosition();
        position.latitude = latitude.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
        position.longitude = longitude.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
        position.accuracyMeters = accuracyMeters == null
                ? null
                : accuracyMeters.setScale(ACCURACY_SCALE, RoundingMode.HALF_UP);
        position.capturedAtDevice = Objects.requireNonNull(capturedAtDevice, "capturedAtDevice must not be null");
        return position;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getAccuracyMeters() {
        return accuracyMeters;
    }

    public Instant getCapturedAtDevice() {
        return capturedAtDevice;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FieldPosition position)) {
            return false;
        }
        return Objects.equals(latitude, position.latitude)
                && Objects.equals(longitude, position.longitude)
                && Objects.equals(accuracyMeters, position.accuracyMeters)
                && Objects.equals(capturedAtDevice, position.capturedAtDevice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, accuracyMeters, capturedAtDevice);
    }
}
