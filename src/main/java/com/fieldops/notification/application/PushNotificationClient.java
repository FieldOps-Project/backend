package com.fieldops.notification.application;

import java.util.UUID;

public interface PushNotificationClient {

    PushDeliveryResult send(String pushToken, UUID inspectionId);
}
