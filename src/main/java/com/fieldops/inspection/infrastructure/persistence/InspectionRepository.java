package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {
}
