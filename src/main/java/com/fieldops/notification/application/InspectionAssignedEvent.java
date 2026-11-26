package com.fieldops.notification.application;

import java.util.Objects;
import java.util.UUID;

public record InspectionAssignedEvent(UUID inspectionId, UUID technicianId) {

    public InspectionAssignedEvent {
        Objects.requireNonNull(inspectionId, "inspectionId must not be null");
        Objects.requireNonNull(technicianId, "technicianId must not be null");
    }
}
