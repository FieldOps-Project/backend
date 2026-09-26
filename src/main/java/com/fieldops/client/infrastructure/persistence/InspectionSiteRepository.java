package com.fieldops.client.infrastructure.persistence;

import com.fieldops.client.domain.model.InspectionSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InspectionSiteRepository extends JpaRepository<InspectionSite, UUID>, JpaSpecificationExecutor<InspectionSite> {
}
