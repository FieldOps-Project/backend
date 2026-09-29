package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionTemplateVersionRepository
        extends JpaRepository<InspectionTemplateVersion, UUID> {

    List<InspectionTemplateVersion> findByTemplateIdOrderByVersionNumberDesc(UUID templateId);

    Optional<InspectionTemplateVersion> findByTemplateIdAndVersionNumber(UUID templateId, int versionNumber);

    boolean existsByTemplateIdAndVersionNumber(UUID templateId, int versionNumber);
}
