package com.fieldops.user.infrastructure.persistence;

import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> withFilters(
            String name,
            String email,
            UserRole role,
            UserStatus status
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            addContains(predicates, criteriaBuilder, root.<String>get("name"), name);
            addContains(predicates, criteriaBuilder, root.<String>get("email"), email);

            if (role != null) {
                predicates.add(criteriaBuilder.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addContains(
            List<Predicate> predicates,
            CriteriaBuilder criteriaBuilder,
            Expression<String> field,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            String normalized = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(field), normalized));
        }
    }
}
