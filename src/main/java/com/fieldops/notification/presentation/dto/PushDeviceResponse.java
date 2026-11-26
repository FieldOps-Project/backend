package com.fieldops.notification.presentation.dto;

import com.fieldops.notification.domain.model.PushDevice;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "PushDeviceResponse", description = "Registered device metadata. The push token is never returned.")
public record PushDeviceResponse(
        UUID id,
        String deviceId,
        String platform,
        Instant updatedAt
) {

    public static PushDeviceResponse from(PushDevice device) {
        return new PushDeviceResponse(device.getId(), device.getDeviceId(), device.getPlatform(), device.getUpdatedAt());
    }
}
