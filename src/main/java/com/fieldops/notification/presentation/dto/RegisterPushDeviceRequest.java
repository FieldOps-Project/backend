package com.fieldops.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterPushDeviceRequest", description = "Push registration owned by the authenticated technician.")
public record RegisterPushDeviceRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(example = "device-7f4a", requiredMode = Schema.RequiredMode.REQUIRED)
        String deviceId,

        @NotBlank
        @Size(max = 512)
        @Schema(example = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]", requiredMode = Schema.RequiredMode.REQUIRED)
        String pushToken,

        @NotBlank
        @Size(max = 32)
        @Schema(example = "ANDROID", requiredMode = Schema.RequiredMode.REQUIRED)
        String platform
) {
}
