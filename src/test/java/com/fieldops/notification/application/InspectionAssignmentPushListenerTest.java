package com.fieldops.notification.application;

import com.fieldops.notification.domain.model.PushDevice;
import com.fieldops.notification.infrastructure.persistence.PushDeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionAssignmentPushListenerTest {

    @Mock
    private ObjectProvider<PushDeviceRepository> repositoryProvider;

    @Mock
    private PushDeviceRepository repository;

    @Mock
    private PushNotificationClient pushNotificationClient;

    @Test
    void removesDeviceWhenExpoReportsItAsUnregistered() {
        UUID technicianId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        PushDevice device = PushDevice.create(technicianId, "device-1", "invalid-token", "ANDROID");
        UUID deviceId = UUID.randomUUID();
        ReflectionTestUtils.setField(device, "id", deviceId);
        when(repositoryProvider.getIfAvailable()).thenReturn(repository);
        when(repository.findAllByUserId(technicianId)).thenReturn(List.of(device));
        when(pushNotificationClient.send("invalid-token", inspectionId)).thenReturn(new PushDeliveryResult(true));

        new InspectionAssignmentPushListener(repositoryProvider, pushNotificationClient)
                .handle(new InspectionAssignedEvent(inspectionId, technicianId));

        verify(repository).deleteById(deviceId);
    }

    @Test
    void swallowsDeliveryFailureSoAssignmentProcessingCanContinue() {
        UUID technicianId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        PushDevice device = PushDevice.create(technicianId, "device-1", "token", "ANDROID");
        when(repositoryProvider.getIfAvailable()).thenReturn(repository);
        when(repository.findAllByUserId(technicianId)).thenReturn(List.of(device));
        when(pushNotificationClient.send("token", inspectionId)).thenThrow(new IllegalStateException("Expo unavailable"));

        assertThatCode(() -> new InspectionAssignmentPushListener(repositoryProvider, pushNotificationClient)
                .handle(new InspectionAssignedEvent(inspectionId, technicianId)))
                .doesNotThrowAnyException();
    }
}
