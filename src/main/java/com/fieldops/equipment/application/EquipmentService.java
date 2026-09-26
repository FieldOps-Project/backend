package com.fieldops.equipment.application;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.domain.model.EquipmentStatus;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.equipment.infrastructure.persistence.EquipmentSpecifications;
import com.fieldops.equipment.presentation.dto.CreateEquipmentRequest;
import com.fieldops.equipment.presentation.dto.UpdateEquipmentRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.presentation.dto.PaginationDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class EquipmentService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "assetNumber", "serialNumber", "manufacturer", "model", "qrCode", "status", "installedAt", "createdAt", "updatedAt"
    );

    private final EquipmentRepository equipmentRepository;
    private final InspectionSiteRepository inspectionSiteRepository;

    public EquipmentService(
            EquipmentRepository equipmentRepository,
            InspectionSiteRepository inspectionSiteRepository
    ) {
        this.equipmentRepository = equipmentRepository;
        this.inspectionSiteRepository = inspectionSiteRepository;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public Equipment createEquipment(CreateEquipmentRequest request) {
        InspectionSite site = inspectionSiteRepository.findById(request.siteId())
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection site", request.siteId()));

        if (site.getStatus() == InspectionSiteStatus.INACTIVE) {
            throw new BusinessRuleException(
                    "INACTIVE_SITE",
                    "Cannot create equipment for an inactive inspection site"
            );
        }

        if (equipmentRepository.existsByQrCode(request.qrCode().trim())) {
            throw new ConflictException(
                    "EQUIPMENT_QR_CODE_ALREADY_EXISTS",
                    "Equipment with QR code '" + request.qrCode().trim() + "' already exists"
            );
        }

        Equipment equipment = Equipment.create(
                request.siteId(),
                request.name(),
                request.assetNumber(),
                request.serialNumber(),
                request.manufacturer(),
                request.model(),
                request.description(),
                request.qrCode(),
                request.status(),
                request.installedAt()
        );

        return equipmentRepository.saveAndFlush(equipment);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public Equipment updateEquipment(UUID id, UpdateEquipmentRequest request) {
        Equipment equipment = getEquipmentById(id);

        if (equipmentRepository.existsByQrCodeAndIdNot(request.qrCode().trim(), id)) {
            throw new ConflictException(
                    "EQUIPMENT_QR_CODE_ALREADY_EXISTS",
                    "Equipment with QR code '" + request.qrCode().trim() + "' already exists"
            );
        }

        equipment.update(
                request.name(),
                request.assetNumber(),
                request.serialNumber(),
                request.manufacturer(),
                request.model(),
                request.description(),
                request.qrCode(),
                request.installedAt()
        );

        return equipment;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public Equipment updateStatus(UUID id, EquipmentStatus newStatus) {
        Equipment equipment = getEquipmentById(id);
        equipment.updateStatus(newStatus);
        return equipment;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.AUTHENTICATED)
    public Equipment getEquipmentById(UUID id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipment", id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.AUTHENTICATED)
    public Equipment getEquipmentByQrCode(String qrCode) {
        if (qrCode == null || qrCode.trim().isEmpty()) {
            throw new InvalidRequestException("INVALID_QR_CODE", "QR code must not be empty");
        }
        return equipmentRepository.findByQrCode(qrCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("EQUIPMENT_NOT_FOUND", "Equipment with QR code '" + qrCode.trim() + "' not found"));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.AUTHENTICATED)
    public Page<Equipment> listEquipment(UUID siteId, EquipmentStatus status, String search, int page, int size, String sort) {
        Pageable pageable = PaginationDefaults.of(page, size, parseSort(sort));
        Specification<Equipment> spec = Specification
                .where(EquipmentSpecifications.hasSiteId(siteId))
                .and(EquipmentSpecifications.hasStatus(status))
                .and(EquipmentSpecifications.hasSearchTerm(search));

        return equipmentRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.AUTHENTICATED)
    public Page<Equipment> listEquipmentBySite(UUID siteId, EquipmentStatus status, String search, int page, int size, String sort) {
        if (!inspectionSiteRepository.existsById(siteId)) {
            throw ResourceNotFoundException.of("Inspection site", siteId);
        }
        return listEquipment(siteId, status, search, page, size, sort);
    }

    private Sort parseSort(String sort) {
        String requestedSort = sort == null || sort.isBlank() ? "createdAt,desc" : sort.trim();
        String[] parts = requestedSort.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank() || !ALLOWED_SORT_FIELDS.contains(parts[0].trim())) {
            throw new InvalidRequestException(
                    "INVALID_SORT",
                    "Sort must use an allowed equipment field and direction asc or desc"
            );
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException ex) {
                throw new InvalidRequestException(
                        "INVALID_SORT",
                        "Sort must use an allowed equipment field and direction asc or desc"
                );
            }
        }
        return Sort.by(direction, parts[0].trim());
    }
}
