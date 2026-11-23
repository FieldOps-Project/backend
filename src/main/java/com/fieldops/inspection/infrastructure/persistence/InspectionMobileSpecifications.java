package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InspectionMobileSpecifications {

    private InspectionMobileSpecifications() {
    }

    public static Specification<Inspection> forTechnician(
            UUID technicianId,
            InspectionStatus status,
            Instant updatedSince
    ) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("technicianId"), technicianId));
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (updatedSince != null) {
                predicates.add(criteriaBuilder.greaterThan(
                        root.<Instant>get("updatedAt"), updatedSince
                ));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
