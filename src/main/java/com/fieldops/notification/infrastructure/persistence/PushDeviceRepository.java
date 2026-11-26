package com.fieldops.notification.infrastructure.persistence;

import com.fieldops.notification.domain.model.PushDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {

    Optional<PushDevice> findByUserIdAndDeviceId(UUID userId, String deviceId);

    @Transactional(readOnly = true)
    List<PushDevice> findAllByUserId(UUID userId);

    long deleteByUserIdAndDeviceId(UUID userId, String deviceId);
}
