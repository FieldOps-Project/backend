package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionTemplateVersionRepository
        extends JpaRepository<InspectionTemplateVersion, UUID> {

    List<InspectionTemplateVersion> findByTemplateIdOrderByVersionNumberDesc(UUID templateId);

    List<InspectionTemplateVersion> findByTemplateIdAndPublishedAtIsNotNullOrderByVersionNumberDesc(UUID templateId);

    Optional<InspectionTemplateVersion> findByTemplateIdAndVersionNumber(UUID templateId, int versionNumber);

    boolean existsByTemplateIdAndVersionNumber(UUID templateId, int versionNumber);

    @Query("select coalesce(max(v.versionNumber), 0) from InspectionTemplateVersion v where v.template.id = :templateId")
    int findLatestVersionNumber(@Param("templateId") UUID templateId);
}
