package com.fieldops.client.infrastructure.persistence;

import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClientSpecifications {

    private ClientSpecifications() {
    }

    public static Specification<Client> withFilters(String search, ClientStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String normalized = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        contains(criteriaBuilder, root.<String>get("name"), normalized),
                        contains(criteriaBuilder, root.<String>get("legalName"), normalized)
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
