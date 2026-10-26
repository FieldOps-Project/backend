package com.fieldops.inspection.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldPositionTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-10-23T13:02:03Z");

    @Test
    void keepsLatitudeLongitudeAccuracyAndTheMomentOfTheReading() {
        FieldPosition position = FieldPosition.of(
                new BigDecimal("-23.50121234"), new BigDecimal("-47.4531"), new BigDecimal("18.456"), CAPTURED_AT
        );

        assertThat(position.getLatitude()).isEqualByComparingTo("-23.501212");
        assertThat(position.getLongitude()).isEqualByComparingTo("-47.453100");
        assertThat(position.getAccuracyMeters()).isEqualByComparingTo("18.46");
        assertThat(position.getCapturedAtDevice()).isEqualTo(CAPTURED_AT);
    }

    @Test
    void accuracyIsOptionalAndPoorAccuracyIsStillAPosition() {
        assertThat(FieldPosition.of(BigDecimal.ONE, BigDecimal.ONE, null, CAPTURED_AT).getAccuracyMeters()).isNull();
        assertThat(FieldPosition.of(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("2500"), CAPTURED_AT)
                .getAccuracyMeters()).isEqualByComparingTo("2500");
    }

    @Test
    void rejectsCoordinatesOutsideTheValidRange() {
        assertThatThrownBy(() -> FieldPosition.of(new BigDecimal("90.1"), BigDecimal.ZERO, null, CAPTURED_AT))
                .hasMessageContaining("latitude");
        assertThatThrownBy(() -> FieldPosition.of(BigDecimal.ZERO, new BigDecimal("-180.5"), null, CAPTURED_AT))
                .hasMessageContaining("longitude");
        assertThatThrownBy(() -> FieldPosition.of(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1"), CAPTURED_AT))
                .hasMessageContaining("accuracyMeters");
    }

    @Test
    void requiresBothCoordinatesAndTheMoment() {
        assertThatThrownBy(() -> FieldPosition.of(null, BigDecimal.ZERO, null, CAPTURED_AT))
                .hasMessageContaining("latitude");
        assertThatThrownBy(() -> FieldPosition.of(BigDecimal.ZERO, BigDecimal.ZERO, null, null))
                .hasMessageContaining("capturedAtDevice");
    }
}
