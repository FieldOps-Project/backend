package com.fieldops.client.application;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteSpecifications;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.presentation.dto.PaginationDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Service
public class InspectionSiteService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "city", "state", "status", "createdAt", "updatedAt"
    );

    private final InspectionSiteRepository inspectionSiteRepository;
    private final ClientRepository clientRepository;

    public InspectionSiteService(
            InspectionSiteRepository inspectionSiteRepository,
            ClientRepository clientRepository
    ) {
        this.inspectionSiteRepository = inspectionSiteRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public InspectionSite createSite(
            UUID clientId,
            String name,
            String description,
            String addressLine,
            String city,
            String state,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String contactName,
            String contactPhone,
            InspectionSiteStatus status
    ) {
        validateCoordinates(latitude, longitude);
        Client client = findClientRequired(clientId);
        return inspectionSiteRepository.saveAndFlush(InspectionSite.create(
                client,
                name,
                description,
                addressLine,
                city,
                state,
                postalCode,
                latitude,
                longitude,
                contactName,
                contactPhone,
                status
        ));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public Page<InspectionSite> findAll(
            UUID clientId,
            String search,
            InspectionSiteStatus status,
            int page,
            int size,
            String sort
    ) {
        Pageable pageable = PaginationDefaults.of(page, size, parseSort(sort));
        return inspectionSiteRepository.findAll(
                InspectionSiteSpecifications.withFilters(clientId, search, status),
                pageable
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public Page<InspectionSite> findByClientId(
            UUID clientId,
            String search,
            InspectionSiteStatus status,
            int page,
            int size,
            String sort
    ) {
        findClientRequired(clientId);
        return findAll(clientId, search, status, page, size, sort);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public InspectionSite findById(UUID id) {
        return findRequired(id);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public InspectionSite updateSite(
            UUID id,
            String name,
            String description,
            String addressLine,
            String city,
            String state,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String contactName,
            String contactPhone
    ) {
        validateCoordinates(latitude, longitude);
        InspectionSite site = findRequired(id);
        site.updateProfile(
                name,
                description,
                addressLine,
                city,
                state,
                postalCode,
                latitude,
                longitude,
                contactName,
                contactPhone
        );
        return inspectionSiteRepository.saveAndFlush(site);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public InspectionSite updateStatus(UUID id, InspectionSiteStatus status) {
        InspectionSite site = findRequired(id);
        site.updateStatus(status);
        return inspectionSiteRepository.save(site);
    }

    /**
     * Shared scheduling guard for the future inspection scheduling use case.
     * Historical sites remain queryable but inactive sites and clients cannot
     * be selected for new work.
     */
    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public InspectionSite findForScheduling(UUID id) {
        InspectionSite site = findRequired(id);
        site.ensureAvailableForScheduling();
        site.getClient().ensureAvailableForScheduling();
        return site;
    }

    private InspectionSite findRequired(UUID id) {
        return inspectionSiteRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("InspectionSite", id));
    }

    private Client findClientRequired(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", id));
    }

    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new InvalidRequestException(
                    "INVALID_COORDINATES",
                    "Latitude and longitude must be provided together"
            );
        }
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new InvalidRequestException(
                    "INVALID_COORDINATES",
                    "Latitude must be between -90 and 90"
            );
        }
        if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new InvalidRequestException(
                    "INVALID_COORDINATES",
                    "Longitude must be between -180 and 180"
            );
        }
    }

    private Sort parseSort(String sort) {
        String requestedSort = sort == null || sort.isBlank() ? "createdAt,desc" : sort.trim();
        String[] parts = requestedSort.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank() || !ALLOWED_SORT_FIELDS.contains(parts[0].trim())) {
            throw invalidSort();
        }

        Sort.Direction direction = Sort.Direction.DESC;
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
                "Sort must use an allowed inspection site field and direction asc or desc"
        );
    }
}
