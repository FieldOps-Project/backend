package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.TemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateItemRepository extends JpaRepository<TemplateItem, UUID> {

    List<TemplateItem> findBySectionIdOrderByDisplayOrderAsc(UUID sectionId);
}
