package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.inspection.application.InspectionListQuery;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.user.domain.model.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class InspectionSpecifications {

    private InspectionSpecifications() {
    }

    public static Specification<Inspection> withFilters(
            InspectionListQuery query,
            UUID scopedSupervisorId,
            Instant now
    ) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.search() != null && !query.search().isBlank()) {
                predicates.add(searchPredicate(
                        root,
                        criteriaQuery,
                        criteriaBuilder,
                        query.search().trim().toLowerCase(Locale.ROOT)
                ));
            }
            if (query.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), query.status()));
            }
            if (query.technicianId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("technicianId"), query.technicianId()));
            }
            if (query.clientId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("clientId"), query.clientId()));
            }
            if (query.siteId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("siteId"), query.siteId()));
            }
            if (query.equipmentId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("equipmentId"), query.equipmentId()));
            }
            if (query.priority() != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), query.priority()));
            }
            if (query.scheduledFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.<Instant>get("scheduledFor"), query.scheduledFrom()
                ));
            }
            if (query.scheduledTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.<Instant>get("scheduledFor"), query.scheduledTo()
                ));
            }
            if (scopedSupervisorId != null) {
                predicates.add(criteriaBuilder.equal(root.get("supervisorId"), scopedSupervisorId));
            }
            if (query.overdue()) {
                predicates.add(criteriaBuilder.and(
                        criteriaBuilder.lessThan(root.<Instant>get("scheduledFor"), now),
                        root.get("status").in(InspectionStatus.ASSIGNED, InspectionStatus.IN_PROGRESS)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate searchPredicate(
            Root<Inspection> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            String search
    ) {
        String pattern = "%" + search + "%";
        List<Predicate> matches = new ArrayList<>();
        matches.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern));
        matches.add(existsByName(query, criteriaBuilder, Client.class, "clientId", "name", root, pattern));
        matches.add(existsByName(query, criteriaBuilder, InspectionSite.class, "siteId", "name", root, pattern));
        matches.add(existsByName(query, criteriaBuilder, Equipment.class, "equipmentId", "name", root, pattern));
        matches.add(existsByName(query, criteriaBuilder, User.class, "technicianId", "name", root, pattern));
        return criteriaBuilder.or(matches.toArray(Predicate[]::new));
    }

    private static <T> Predicate existsByName(
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            Class<T> entityType,
            String inspectionIdField,
            String nameField,
            Root<Inspection> inspection,
            String pattern
    ) {
        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<T> related = subquery.from(entityType);
        Expression<UUID> relatedId = related.get("id");
        subquery.select(relatedId).where(
                criteriaBuilder.equal(relatedId, inspection.get(inspectionIdField)),
                criteriaBuilder.like(criteriaBuilder.lower(related.get(nameField)), pattern)
        );
        return criteriaBuilder.exists(subquery);
    }
}
