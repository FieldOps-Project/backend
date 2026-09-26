package com.fieldops.equipment.infrastructure.persistence;

import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.domain.model.EquipmentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class EquipmentSpecifications {

    private EquipmentSpecifications() {
    }

    public static Specification<Equipment> hasSiteId(UUID siteId) {
        return (root, query, cb) ->
                siteId == null ? null : cb.equal(root.get("siteId"), siteId);
    }

    public static Specification<Equipment> hasStatus(EquipmentStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Equipment> hasSearchTerm(String search) {
        return (root, query, cb) -> {
            if (search == null || search.trim().isEmpty()) {
                return null;
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("assetNumber")), pattern),
                    cb.like(cb.lower(root.get("serialNumber")), pattern),
                    cb.like(cb.lower(root.get("manufacturer")), pattern),
                    cb.like(cb.lower(root.get("model")), pattern),
                    cb.like(cb.lower(root.get("qrCode")), pattern)
            );
        };
    }
}
