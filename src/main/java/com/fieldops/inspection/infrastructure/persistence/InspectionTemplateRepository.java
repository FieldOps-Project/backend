package com.fieldops.inspection.infrastructure.persistence;

import com.fieldops.inspection.domain.model.InspectionTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InspectionTemplateRepository
        extends JpaRepository<InspectionTemplate, UUID>, JpaSpecificationExecutor<InspectionTemplate> {

    boolean existsByTitleIgnoreCase(String title);

    boolean existsByTitleIgnoreCaseAndIdNot(String title, UUID id);

    /**
     * Row lock that serializes concurrent publications of the same template,
     * so version numbers are assigned one at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InspectionTemplate t where t.id = :id")
    Optional<InspectionTemplate> findByIdForUpdate(@Param("id") UUID id);
}
