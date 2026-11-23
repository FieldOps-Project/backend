package com.fieldops.inspection.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InspectionResponseValue(
        String valueText,
        BigDecimal valueNumber,
        Boolean valueBoolean,
        LocalDate valueDate,
        String valueJson,
        InspectionResponseConformity conformity
) {
}
