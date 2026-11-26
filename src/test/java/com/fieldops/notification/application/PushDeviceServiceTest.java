package com.fieldops.notification.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.notification.domain.model.PushDevice;
import com.fieldops.notification.infrastructure.persistence.PushDeviceRepository;
import com.fieldops.notification.presentation.dto.RegisterPushDeviceRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushDeviceServiceTest {

    @Mock
    private PushDeviceRepository repository;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    private final Authentication authentication = new TestingAuthenticationToken("technician", "password");
    private final UUID technicianId = UUID.randomUUID();
    private PushDeviceService service;

    @BeforeEach
    void setUp() {
        service = new PushDeviceService(repository, authenticatedUserResolver);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registersNewDeviceWithoutReturningThePushTokenAsResponseData() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(technicianId);
        when(repository.findByUserIdAndDeviceId(technicianId, "device-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(PushDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PushDevice device = service.register(new RegisterPushDeviceRequest(
                " device-1 ", " ExponentPushToken[token] ", "android"
        ));

        assertThat(device.getUserId()).isEqualTo(technicianId);
        assertThat(device.getDeviceId()).isEqualTo("device-1");
        assertThat(device.getPushToken()).isEqualTo("ExponentPushToken[token]");
        assertThat(device.getPlatform()).isEqualTo("ANDROID");
        verify(repository).saveAndFlush(device);
    }

    @Test
    void updatesAnExistingDeviceRegistrationAndRemovesOnlyTheAuthenticatedOwnerDevice() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authenticatedUserResolver.requireUserId(authentication)).thenReturn(technicianId);
        PushDevice existing = PushDevice.create(technicianId, "device-1", "old-token", "IOS");
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        when(repository.findByUserIdAndDeviceId(technicianId, "device-1")).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        PushDevice updated = service.register(new RegisterPushDeviceRequest(
                "device-1", "new-token", "ios"
        ));
        service.remove(" device-1 ");

        assertThat(updated.getPushToken()).isEqualTo("new-token");
        assertThat(updated.getPlatform()).isEqualTo("IOS");
        verify(repository).deleteByUserIdAndDeviceId(technicianId, "device-1");
    }
}
