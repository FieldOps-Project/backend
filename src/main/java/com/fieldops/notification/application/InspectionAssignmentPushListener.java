package com.fieldops.notification.application;

import com.fieldops.notification.domain.model.PushDevice;
import com.fieldops.notification.infrastructure.persistence.PushDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InspectionAssignmentPushListener {

    private static final Logger log = LoggerFactory.getLogger(InspectionAssignmentPushListener.class);

    private final ObjectProvider<PushDeviceRepository> repositoryProvider;
    private final PushNotificationClient pushNotificationClient;

    public InspectionAssignmentPushListener(
            ObjectProvider<PushDeviceRepository> repositoryProvider,
            PushNotificationClient pushNotificationClient
    ) {
        this.repositoryProvider = repositoryProvider;
        this.pushNotificationClient = pushNotificationClient;
    }

    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(InspectionAssignedEvent event) {
        try {
            for (PushDevice device : repository().findAllByUserId(event.technicianId())) {
                try {
                    PushDeliveryResult result = pushNotificationClient.send(device.getPushToken(), event.inspectionId());
                    if (result.invalidToken()) {
                        repository().deleteById(device.getId());
                    }
                } catch (Exception exception) {
                    log.warn("Push delivery failed for technician {}: {}",
                            event.technicianId(), exception.getClass().getSimpleName());
                }
            }
        } catch (Exception exception) {
            log.warn("Push notification processing failed for technician {}: {}",
                    event.technicianId(), exception.getClass().getSimpleName());
        }
    }

    private PushDeviceRepository repository() {
        PushDeviceRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new IllegalStateException("Push device persistence is not available");
        }
        return repository;
    }
}
