package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.FieldPosition;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionEvidence;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.inspection.domain.model.InspectionReview;
import com.fieldops.inspection.domain.model.NonConformity;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionReviewRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.inspection.infrastructure.report.InspectionReportPdfGenerator;
import com.fieldops.inspection.infrastructure.storage.EvidenceStorage;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InspectionReportService {

    private final InspectionRepository inspectionRepository;
    private final ClientRepository clientRepository;
    private final InspectionSiteRepository siteRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final InspectionResponseRepository responseRepository;
    private final InspectionEvidenceRepository evidenceRepository;
    private final NonConformityRepository nonConformityRepository;
    private final InspectionReviewRepository reviewRepository;
    private final EvidenceStorage evidenceStorage;
    private final AuditEventService auditEventService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final InspectionReportPdfGenerator pdfGenerator;

    public InspectionReportService(
            InspectionRepository inspectionRepository,
            ClientRepository clientRepository,
            InspectionSiteRepository siteRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            InspectionResponseRepository responseRepository,
            InspectionEvidenceRepository evidenceRepository,
            NonConformityRepository nonConformityRepository,
            InspectionReviewRepository reviewRepository,
            EvidenceStorage evidenceStorage,
            AuditEventService auditEventService,
            AuthenticatedUserResolver authenticatedUserResolver,
            InspectionReportPdfGenerator pdfGenerator
    ) {
        this.inspectionRepository = inspectionRepository;
        this.clientRepository = clientRepository;
        this.siteRepository = siteRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.responseRepository = responseRepository;
        this.evidenceRepository = evidenceRepository;
        this.nonConformityRepository = nonConformityRepository;
        this.reviewRepository = reviewRepository;
        this.evidenceStorage = evidenceStorage;
        this.auditEventService = auditEventService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.pdfGenerator = pdfGenerator;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public byte[] generate(UUID inspectionId) {
        Inspection inspection = loadInScope(inspectionId);
        List<InspectionResponse> responses = responseRepository.findAllByInspectionIdOrderByAnsweredAtServerAsc(inspectionId);
        List<InspectionEvidence> evidences = evidenceRepository
                .findAllByInspectionIdAndDeletedAtIsNullOrderByServerReceivedAtAsc(inspectionId);
        List<NonConformity> nonConformities = nonConformityRepository
                .findAllByInspectionIdOrderByCreatedAtAsc(inspectionId);
        InspectionReview review = reviewRepository.findFirstByInspectionIdOrderByReviewCycleDesc(inspectionId).orElse(null);
        InspectionReportData report = mapReport(inspection, responses, evidences, nonConformities, review);
        byte[] pdf = pdfGenerator.generate(report);
        auditEventService.recordInspectionEvent(
                "INSPECTION_REPORT_EXPORTED",
                "INSPECTION_REPORT",
                inspectionId,
                inspectionId,
                null,
                null,
                "{\"format\":\"PDF\"}"
        );
        return pdf;
    }

    private Inspection loadInScope(UUID inspectionId) {
        User user = authenticatedUserResolver.requireUser(SecurityContextHolder.getContext().getAuthentication());
        if (user.getRole() == UserRole.SUPERVISOR) {
            return inspectionRepository.findWithSnapshotsByIdAndSupervisorId(inspectionId, user.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
        }
        if (user.getRole() == UserRole.ADMIN) {
            return inspectionRepository.findWithSnapshotsById(inspectionId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
        }
        throw ResourceNotFoundException.of("Inspection", inspectionId);
    }

    private InspectionReportData mapReport(
            Inspection inspection,
            List<InspectionResponse> responses,
            List<InspectionEvidence> evidences,
            List<NonConformity> nonConformities,
            InspectionReview review
    ) {
        Client client = clientRepository.findById(inspection.getClientId()).orElse(null);
        InspectionSite site = siteRepository.findById(inspection.getSiteId()).orElse(null);
        Equipment equipment = inspection.getEquipmentId() == null
                ? null : equipmentRepository.findById(inspection.getEquipmentId()).orElse(null);
        User technician = inspection.getTechnicianId() == null
                ? null : userRepository.findById(inspection.getTechnicianId()).orElse(null);
        User supervisor = inspection.getSupervisorId() == null
                ? null : userRepository.findById(inspection.getSupervisorId()).orElse(null);
        User reviewer = review == null ? null : userRepository.findById(review.getReviewerId()).orElse(null);

        Map<UUID, InspectionResponse> responsesByItem = responses.stream()
                .collect(Collectors.toMap(InspectionResponse::getInspectionItemId, Function.identity()));
        Map<UUID, List<InspectionReportData.Evidence>> evidenceByResponse = new HashMap<>();
        Map<UUID, List<InspectionReportData.Evidence>> evidenceByNonConformity = new HashMap<>();
        List<InspectionReportData.Evidence> unlinkedEvidence = new ArrayList<>();
        Map<UUID, InspectionResponse> responsesById = responses.stream()
                .collect(Collectors.toMap(InspectionResponse::getId, Function.identity()));
        Map<UUID, NonConformity> nonConformitiesById = nonConformities.stream()
                .collect(Collectors.toMap(NonConformity::getId, Function.identity()));
        for (InspectionEvidence evidence : evidences) {
            InspectionReportData.Evidence reportEvidence = mapEvidence(evidence);
            if (evidence.getResponseId() != null && responsesById.containsKey(evidence.getResponseId())) {
                evidenceByResponse.computeIfAbsent(evidence.getResponseId(), ignored -> new ArrayList<>()).add(reportEvidence);
            } else if (evidence.getNonConformityId() != null
                    && nonConformitiesById.containsKey(evidence.getNonConformityId())) {
                evidenceByNonConformity.computeIfAbsent(evidence.getNonConformityId(), ignored -> new ArrayList<>()).add(reportEvidence);
            } else {
                unlinkedEvidence.add(reportEvidence);
            }
        }

        List<InspectionReportData.Item> items = inspection.getItemSnapshots().stream()
                .map(snapshot -> mapItem(snapshot, responsesByItem.get(snapshot.getId()), evidenceByResponse))
                .toList();
        List<InspectionReportData.NonConformity> reportNonConformities = nonConformities.stream()
                .map(nonConformity -> new InspectionReportData.NonConformity(
                        nonConformity.getTitle(), nonConformity.getDescription(),
                        nonConformity.getSeverity().name(), nonConformity.getStatus().name(),
                        evidenceByNonConformity.getOrDefault(nonConformity.getId(), List.of())
                ))
                .toList();
        InspectionReportData.Review reportReview = review == null ? null : new InspectionReportData.Review(
                review.getDecision().name(), nameOf(reviewer), review.getReason(), review.getComments(),
                review.getReviewedAt(), review.getReviewCycle(), review.getItemsToCorrect()
        );

        return new InspectionReportData(
                inspection.getId(), inspection.getTitle(), inspection.getInstructions(), inspection.getStatus().name(),
                inspection.getPriority().name(),
                inspection.getScheduledFor(), nameOf(client), nameOf(site), addressOf(site), nameOf(equipment),
                nameOf(technician), nameOf(supervisor), inspection.getStartedAtDevice(), inspection.getStartedAtServer(),
                inspection.getCompletedAtDevice(), inspection.getSubmittedAtServer(), locationOf(inspection.getStartPosition()),
                locationOf(inspection.getCompletionPosition()), items, reportNonConformities, reportReview, unlinkedEvidence
        );
    }

    private InspectionReportData.Item mapItem(
            InspectionItemSnapshot snapshot,
            InspectionResponse response,
            Map<UUID, List<InspectionReportData.Evidence>> evidenceByResponse
    ) {
        return new InspectionReportData.Item(
                snapshot.getSectionTitle(), snapshot.getSectionDescription(), snapshot.getSectionOrder(),
                snapshot.getItemCode(), snapshot.getItemTitle(), snapshot.getItemDescription(), snapshot.getResponseType(),
                snapshot.isRequired(), response == null ? null : responseValue(response),
                response == null ? null : response.getObservation(),
                response == null ? List.of() : evidenceByResponse.getOrDefault(response.getId(), List.of())
        );
    }

    private InspectionReportData.Evidence mapEvidence(InspectionEvidence evidence) {
        byte[] image = null;
        if (evidence.isFileReceived()) {
            try {
                image = evidenceStorage.read(evidence.getStorageKey());
            } catch (RuntimeException ignored) {
                // Keep the metadata in the report when the private object is temporarily unavailable.
            }
        }
        return new InspectionReportData.Evidence(
                evidence.getType(), evidence.getDescription(), evidence.getCapturedAtDevice(),
                evidence.getServerReceivedAt(), image
        );
    }

    private String responseValue(InspectionResponse response) {
        if (response.getConformity() != null) return response.getConformity().name();
        if (response.getValueText() != null) return response.getValueText();
        if (response.getValueNumber() != null) return response.getValueNumber().toPlainString();
        if (response.getValueBoolean() != null) return response.getValueBoolean().toString();
        if (response.getValueDate() != null) return response.getValueDate().toString();
        return response.getValueJson();
    }

    private String nameOf(Client client) { return client == null ? "Nao informado" : client.getName(); }
    private String nameOf(InspectionSite site) { return site == null ? "Nao informado" : site.getName(); }
    private String nameOf(Equipment equipment) { return equipment == null ? "Nao informado" : equipment.getName(); }
    private String nameOf(User user) { return user == null ? "Nao informado" : user.getName(); }

    private String addressOf(InspectionSite site) {
        if (site == null) return "Nao informado";
        return java.util.stream.Stream.of(site.getAddressLine(), site.getCity(), site.getState(), site.getPostalCode())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(", "));
    }

    private String locationOf(FieldPosition position) {
        if (position == null) return "Nao informado";
        return position.getLatitude() + ", " + position.getLongitude()
                + " (precisao " + position.getAccuracyMeters() + " m)";
    }
}
