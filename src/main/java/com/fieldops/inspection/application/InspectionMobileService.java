package com.fieldops.inspection.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionMobileSpecifications;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.user.domain.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnBean(InspectionRepository.class)
public class InspectionMobileService {

    private final InspectionRepository inspectionRepository;
    private final ClientRepository clientRepository;
    private final InspectionSiteRepository siteRepository;
    private final EquipmentRepository equipmentRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Autowired
    public InspectionMobileService(
            InspectionRepository inspectionRepository,
            ClientRepository clientRepository,
            InspectionSiteRepository siteRepository,
            EquipmentRepository equipmentRepository,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepository = inspectionRepository;
        this.clientRepository = clientRepository;
        this.siteRepository = siteRepository;
        this.equipmentRepository = equipmentRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public List<InspectionMobileSummary> findAll(InspectionMobileQuery query) {
        UUID technicianId = authenticatedTechnicianId();
        List<Inspection> inspections = inspectionRepository.findAll(
                InspectionMobileSpecifications.forTechnician(
                        technicianId, query.status(), query.updatedSince()
                ),
                Sort.by(Sort.Direction.ASC, "scheduledFor")
        );
        RelatedEntities related = loadRelatedEntities(inspections);
        return inspections.stream()
                .map(inspection -> toSummary(inspection, related))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    public InspectionMobileDetail findById(UUID id) {
        Inspection inspection = inspectionRepository.findWithSnapshotsByIdAndTechnicianId(
                        id, authenticatedTechnicianId()
                )
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection", id));
        RelatedEntities related = loadRelatedEntities(List.of(inspection));
        return toDetail(inspection, related);
    }

    private UUID authenticatedTechnicianId() {
        User authenticatedUser = authenticatedUserResolver.requireUser(
                SecurityContextHolder.getContext().getAuthentication()
        );
        return authenticatedUser.getId();
    }

    private RelatedEntities loadRelatedEntities(List<Inspection> inspections) {
        Set<UUID> clientIds = inspections.stream()
                .map(Inspection::getClientId)
                .collect(Collectors.toSet());
        Set<UUID> siteIds = inspections.stream()
                .map(Inspection::getSiteId)
                .collect(Collectors.toSet());
        Set<UUID> equipmentIds = inspections.stream()
                .map(Inspection::getEquipmentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        return new RelatedEntities(
                indexById(clientRepository.findAllById(clientIds), Client::getId),
                indexById(siteRepository.findAllById(siteIds), InspectionSite::getId),
                indexById(equipmentRepository.findAllById(equipmentIds), Equipment::getId)
        );
    }

    private InspectionMobileSummary toSummary(Inspection inspection, RelatedEntities related) {
        Client client = related.clients().get(inspection.getClientId());
        InspectionSite site = related.sites().get(inspection.getSiteId());
        Equipment equipment = related.equipment().get(inspection.getEquipmentId());
        return new InspectionMobileSummary(
                inspection.getId(),
                inspection.getTitle(),
                inspection.getStatus(),
                inspection.getPriority(),
                inspection.getScheduledFor(),
                inspection.getUpdatedAt(),
                inspection.getVersion(),
                inspection.getClientId(),
                client == null ? null : client.getName(),
                inspection.getSiteId(),
                site == null ? null : site.getName(),
                inspection.getEquipmentId(),
                equipment == null ? null : equipment.getName()
        );
    }

    private InspectionMobileDetail toDetail(Inspection inspection, RelatedEntities related) {
        Client client = related.clients().get(inspection.getClientId());
        InspectionSite site = related.sites().get(inspection.getSiteId());
        Equipment equipment = related.equipment().get(inspection.getEquipmentId());
        List<InspectionMobileChecklistItem> items = inspection.getItemSnapshots().stream()
                .sorted(Comparator.comparingInt(InspectionItemSnapshot::getSectionOrder)
                        .thenComparingInt(InspectionItemSnapshot::getItemOrder))
                .map(this::toChecklistItem)
                .toList();
        InspectionMobileReview review = inspection.getStatus() == InspectionStatus.REJECTED
                ? new InspectionMobileReview(InspectionStatus.REJECTED, null, List.of())
                : null;

        return new InspectionMobileDetail(
                inspection.getId(),
                inspection.getTitle(),
                inspection.getStatus(),
                inspection.getPriority(),
                inspection.getScheduledFor(),
                inspection.getUpdatedAt(),
                inspection.getInstructions(),
                inspection.getVersion(),
                inspection.getClientId(),
                client == null ? null : client.getName(),
                inspection.getSiteId(),
                site == null ? null : site.getName(),
                site == null ? null : site.getAddressLine(),
                site == null ? null : site.getLatitude(),
                site == null ? null : site.getLongitude(),
                inspection.getEquipmentId(),
                equipment == null ? null : equipment.getName(),
                equipment == null ? null : equipment.getQrCode(),
                equipment == null ? null : equipment.getAssetNumber(),
                equipment == null ? null : equipment.getSerialNumber(),
                items,
                List.of(),
                review
        );
    }

    private InspectionMobileChecklistItem toChecklistItem(InspectionItemSnapshot snapshot) {
        return new InspectionMobileChecklistItem(
                snapshot.getId(),
                snapshot.getSectionTitle(),
                snapshot.getSectionDescription(),
                snapshot.getSectionOrder(),
                snapshot.getItemCode(),
                snapshot.getItemTitle(),
                snapshot.getItemDescription(),
                snapshot.getResponseType(),
                snapshot.isRequired(),
                snapshot.getRulesJson(),
                snapshot.getOptionsJson(),
                snapshot.getItemOrder()
        );
    }

    private <T> Map<UUID, T> indexById(Iterable<T> entities, Function<T, UUID> idExtractor) {
        Map<UUID, T> indexed = new HashMap<>();
        entities.forEach(entity -> indexed.put(idExtractor.apply(entity), entity));
        return indexed;
    }

    private record RelatedEntities(
            Map<UUID, Client> clients,
            Map<UUID, InspectionSite> sites,
            Map<UUID, Equipment> equipment
    ) {
    }
}
