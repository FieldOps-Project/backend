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
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionItemSnapshotRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionProgressProjection;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionSpecifications;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.presentation.dto.PaginationDefaults;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InspectionListService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "title", "priority", "status", "scheduledFor", "createdAt", "updatedAt"
    );

    private final ObjectProvider<InspectionRepository> inspectionRepositoryProvider;
    private final InspectionRepository directInspectionRepository;
    private final InspectionItemSnapshotRepository snapshotRepository;
    private final ClientRepository clientRepository;
    private final InspectionSiteRepository inspectionSiteRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Autowired
    public InspectionListService(
            ObjectProvider<InspectionRepository> inspectionRepositoryProvider,
            InspectionItemSnapshotRepository snapshotRepository,
            ClientRepository clientRepository,
            InspectionSiteRepository inspectionSiteRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepositoryProvider = inspectionRepositoryProvider;
        this.directInspectionRepository = null;
        this.snapshotRepository = snapshotRepository;
        this.clientRepository = clientRepository;
        this.inspectionSiteRepository = inspectionSiteRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    /** Constructor kept available for application-service unit tests. */
    public InspectionListService(
            InspectionRepository inspectionRepository,
            InspectionItemSnapshotRepository snapshotRepository,
            ClientRepository clientRepository,
            InspectionSiteRepository inspectionSiteRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepositoryProvider = null;
        this.directInspectionRepository = inspectionRepository;
        this.snapshotRepository = snapshotRepository;
        this.clientRepository = clientRepository;
        this.inspectionSiteRepository = inspectionSiteRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public Page<InspectionListItem> findAll(InspectionListQuery query) {
        User authenticatedUser = authenticatedUserResolver.requireUser(
                SecurityContextHolder.getContext().getAuthentication()
        );
        UUID scopedSupervisorId = authenticatedUser.getRole() == UserRole.SUPERVISOR
                ? authenticatedUser.getId()
                : query.supervisorId();

        Pageable pageable = PaginationDefaults.of(query.page(), query.size(), parseSort(query.sort()));
        Page<Inspection> page = inspectionRepository().findAll(
                InspectionSpecifications.withFilters(query, scopedSupervisorId, Instant.now()),
                pageable
        );

        if (page.isEmpty()) {
            return page.map(inspection -> toListItem(
                    inspection,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Instant.now()
            ));
        }

        Set<UUID> inspectionIds = page.getContent().stream()
                .map(Inspection::getId)
                .collect(Collectors.toSet());
        Map<UUID, Integer> applicableItems = snapshotRepository.countApplicableItems(inspectionIds).stream()
                .collect(Collectors.toMap(
                        InspectionProgressProjection::getInspectionId,
                        projection -> Math.toIntExact(projection.getApplicableItems())
                ));

        Set<UUID> clientIds = page.getContent().stream().map(Inspection::getClientId).collect(Collectors.toSet());
        Set<UUID> siteIds = page.getContent().stream().map(Inspection::getSiteId).collect(Collectors.toSet());
        Set<UUID> equipmentIds = page.getContent().stream()
                .map(Inspection::getEquipmentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<UUID> technicianIds = page.getContent().stream()
                .map(Inspection::getTechnicianId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<UUID, Client> clients = indexById(clientRepository.findAllById(clientIds), Client::getId);
        Map<UUID, InspectionSite> sites = indexById(inspectionSiteRepository.findAllById(siteIds), InspectionSite::getId);
        Map<UUID, Equipment> equipment = indexById(equipmentRepository.findAllById(equipmentIds), Equipment::getId);
        Map<UUID, User> technicians = indexById(userRepository.findAllById(technicianIds), User::getId);
        Instant now = Instant.now();

        return page.map(inspection -> toListItem(
                inspection,
                clients,
                sites,
                equipment,
                technicians,
                applicableItems,
                now
        ));
    }

    private InspectionListItem toListItem(
            Inspection inspection,
            Map<UUID, Client> clients,
            Map<UUID, InspectionSite> sites,
            Map<UUID, Equipment> equipment,
            Map<UUID, User> technicians,
            Map<UUID, Integer> applicableItems,
            Instant now
    ) {
        Client client = clients.get(inspection.getClientId());
        InspectionSite site = sites.get(inspection.getSiteId());
        Equipment selectedEquipment = equipment.get(inspection.getEquipmentId());
        User technician = technicians.get(inspection.getTechnicianId());
        int totalItems = applicableItems.getOrDefault(inspection.getId(), 0);

        return new InspectionListItem(
                inspection.getId(),
                inspection.getTitle(),
                inspection.getClientId(),
                client == null ? null : client.getName(),
                inspection.getSiteId(),
                site == null ? null : site.getName(),
                inspection.getEquipmentId(),
                selectedEquipment == null ? null : selectedEquipment.getName(),
                inspection.getTechnicianId(),
                technician == null ? null : technician.getName(),
                inspection.getPriority(),
                inspection.getStatus(),
                inspection.getScheduledFor(),
                0,
                totalItems,
                inspection.getUpdatedAt(),
                isOverdue(inspection, now)
        );
    }

    private boolean isOverdue(Inspection inspection, Instant now) {
        return inspection.getScheduledFor().isBefore(now)
                && (inspection.getStatus() == InspectionStatus.ASSIGNED
                || inspection.getStatus() == InspectionStatus.IN_PROGRESS);
    }

    private Sort parseSort(String sort) {
        String requestedSort = sort == null || sort.isBlank() ? "scheduledFor,asc" : sort.trim();
        String[] parts = requestedSort.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank() || !ALLOWED_SORT_FIELDS.contains(parts[0].trim())) {
            throw invalidSort();
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException ex) {
                throw invalidSort();
            }
        }
        return Sort.by(direction, parts[0].trim());
    }

    private InvalidRequestException invalidSort() {
        return new InvalidRequestException(
                "INVALID_SORT",
                "Sort must use an allowed inspection field and direction asc or desc"
        );
    }

    private InspectionRepository inspectionRepository() {
        if (directInspectionRepository != null) {
            return directInspectionRepository;
        }
        InspectionRepository repository = inspectionRepositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new IllegalStateException("Inspection persistence is not available");
        }
        return repository;
    }

    private <T> Map<UUID, T> indexById(Iterable<T> entities, Function<T, UUID> idExtractor) {
        Map<UUID, T> indexed = new HashMap<>();
        entities.forEach(entity -> indexed.put(idExtractor.apply(entity), entity));
        return indexed;
    }
}
