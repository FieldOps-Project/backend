package com.fieldops.notification.presentation;

import com.fieldops.notification.domain.model.PushDevice;
import com.fieldops.notification.presentation.dto.PushDeviceResponse;
import org.springframework.stereotype.Component;

@Component
public class PushDeviceMapper {

    public PushDeviceResponse toResponse(PushDevice device) {
        return PushDeviceResponse.from(device);
    }
}
