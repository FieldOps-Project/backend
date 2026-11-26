package com.fieldops.inspection.application;

import com.fieldops.inspection.domain.model.ResponseType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable report input assembled from the inspection snapshot and its related records. */
public record InspectionReportData(
        UUID inspectionId,
        String title,
        String instructions,
        String status,
        String priority,
        Instant scheduledFor,
        String clientName,
        String siteName,
        String siteAddress,
        String equipmentName,
        String technicianName,
        String supervisorName,
        Instant startedAtDevice,
        Instant startedAtServer,
        Instant completedAtDevice,
        Instant submittedAtServer,
        String startLocation,
        String completionLocation,
        List<Item> items,
        List<NonConformity> nonConformities,
        Review review,
        List<Evidence> unlinkedEvidence
) {

    public record Item(
            String sectionTitle,
            String sectionDescription,
            int sectionOrder,
            String code,
            String title,
            String description,
            ResponseType responseType,
            boolean required,
            String response,
            String observation,
            List<Evidence> evidence
    ) {
    }

    public record NonConformity(
            String title,
            String description,
            String severity,
            String status,
            List<Evidence> evidence
    ) {
    }

    public record Review(
            String decision,
            String reviewerName,
            String reason,
            String comments,
            Instant reviewedAt,
            int reviewCycle,
            List<UUID> itemsToCorrect
    ) {
    }

    public record Evidence(
            String type,
            String description,
            Instant capturedAtDevice,
            Instant serverReceivedAt,
            byte[] image
    ) {
    }
}
