package com.fieldops.client.infrastructure.persistence;

import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class InspectionSiteSpecifications {

    private InspectionSiteSpecifications() {
    }

    public static Specification<InspectionSite> withFilters(
            UUID clientId,
            String search,
            InspectionSiteStatus status
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (clientId != null) {
                predicates.add(criteriaBuilder.equal(root.get("client").get("id"), clientId));
            }

            if (search != null && !search.isBlank()) {
                String normalized = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        contains(criteriaBuilder, root.<String>get("name"), normalized),
                        contains(criteriaBuilder, root.<String>get("description"), normalized),
                        contains(criteriaBuilder, root.<String>get("city"), normalized),
                        contains(criteriaBuilder, root.<String>get("state"), normalized)
                ));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate contains(
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            Expression<String> field,
            String value
    ) {
        return criteriaBuilder.like(criteriaBuilder.lower(field), value);
    }
}
