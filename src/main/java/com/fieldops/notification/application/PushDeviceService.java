package com.fieldops.notification.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.notification.domain.model.PushDevice;
import com.fieldops.notification.infrastructure.persistence.PushDeviceRepository;
import com.fieldops.notification.presentation.dto.RegisterPushDeviceRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PushDeviceService {

    private final ObjectProvider<PushDeviceRepository> repositoryProvider;
    private final PushDeviceRepository directRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Autowired
    public PushDeviceService(
            ObjectProvider<PushDeviceRepository> repositoryProvider,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.repositoryProvider = repositoryProvider;
        this.directRepository = null;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    PushDeviceService(PushDeviceRepository repository, AuthenticatedUserResolver authenticatedUserResolver) {
        this.repositoryProvider = null;
        this.directRepository = repository;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public PushDevice register(RegisterPushDeviceRequest request) {
        UUID userId = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );
        String deviceId = normalize(request.deviceId());
        String pushToken = normalize(request.pushToken());
        String platform = normalize(request.platform()).toUpperCase(java.util.Locale.ROOT);

        PushDevice device = repository().findByUserIdAndDeviceId(userId, deviceId)
                .orElseGet(() -> PushDevice.create(userId, deviceId, pushToken, platform));
        if (device.getId() != null) {
            device.updateRegistration(pushToken, platform);
        }
        return repository().saveAndFlush(device);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public void remove(String deviceId) {
        UUID userId = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );
        repository().deleteByUserIdAndDeviceId(userId, normalize(deviceId));
    }

    private PushDeviceRepository repository() {
        if (directRepository != null) {
            return directRepository;
        }
        PushDeviceRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new IllegalStateException("Push device persistence is not available");
        }
        return repository;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
