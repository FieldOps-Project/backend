package com.fieldops.sync.infrastructure.persistence;

import com.fieldops.sync.domain.model.SyncProcessedOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SyncProcessedOperationRepository extends JpaRepository<SyncProcessedOperation, UUID> {
}
