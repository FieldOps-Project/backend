package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.InspectionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface InspectionTemplateRepository
        extends JpaRepository<InspectionTemplate, UUID>, JpaSpecificationExecutor<InspectionTemplate> {

    boolean existsByTitleIgnoreCase(String title);

    boolean existsByTitleIgnoreCaseAndIdNot(String title, UUID id);
}
