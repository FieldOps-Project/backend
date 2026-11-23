package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.domain.model.EquipmentStatus;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.inspection.presentation.dto.CreateInspectionRequest;
import com.fieldops.inspection.presentation.dto.UpdateInspectionRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.domain.exception.Violation;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class InspectionSchedulingService {

    private static final String INSPECTION_ENTITY = "INSPECTION";

    private final InspectionRepository inspectionRepository;
    private final InspectionTemplateVersionRepository versionRepository;
    private final TemplateVersionService templateVersionService;
    private final ClientRepository clientRepository;
    private final InspectionSiteRepository siteRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final AuditEventService auditEventService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public InspectionSchedulingService(
            ObjectProvider<InspectionRepository> inspectionRepositoryProvider,
            InspectionTemplateVersionRepository versionRepository,
            TemplateVersionService templateVersionService,
            ClientRepository clientRepository,
            InspectionSiteRepository siteRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            AuditEventService auditEventService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepository = inspectionRepositoryProvider.getIfAvailable();
        this.versionRepository = versionRepository;
        this.templateVersionService = templateVersionService;
        this.clientRepository = clientRepository;
        this.siteRepository = siteRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.auditEventService = auditEventService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public Inspection schedule(CreateInspectionRequest request) {
        UUID actorId = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );
        validateScheduledFor(request.scheduledFor());
        Client client = findClient(request.clientId());
        InspectionSite site = findSite(request.siteId());
        validateClientSite(client, site);
        client.ensureAvailableForScheduling();
        site.ensureAvailableForScheduling();
        Equipment equipment = validateEquipment(request.equipmentId(), site);
        User technician = request.technicianId() == null
                ? null
                : validateUser(request.technicianId(), UserRole.TECHNICIAN, "technicianId");
        validateSupervisor(request.supervisorId());
        InspectionTemplateVersion version = templateVersionService.findVersionForScheduling(
                request.templateVersionId()
        );
        validateEquipmentRequirement(version, equipment);

        Inspection inspection = Inspection.create(
                version,
                client.getId(),
                site.getId(),
                equipment == null ? null : equipment.getId(),
                technician == null ? null : technician.getId(),
                request.supervisorId(),
                actorId,
                request.title(),
                request.instructions(),
                request.priority(),
                request.scheduledFor()
        );
        Inspection saved = inspectionRepository().saveAndFlush(inspection);
        auditEventService.recordEvent(
                "INSPECTION_CREATED",
                INSPECTION_ENTITY,
                saved.getId(),
                null,
                auditState(saved)
        );
        if (technician != null) {
            auditEventService.recordEvent(
                    "INSPECTION_ASSIGNED",
                    INSPECTION_ENTITY,
                    saved.getId(),
                    null,
                    "technicianId=" + technician.getId()
            );
        }
        return saved;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public Inspection findById(UUID id) {
        return inspectionRepository().findWithSnapshotsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection", id));
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public Inspection update(UUID id, UpdateInspectionRequest request) {
        Inspection inspection = findByIdForUpdate(id);
        if (inspection.getStatus() != InspectionStatus.DRAFT
                && inspection.getStatus() != InspectionStatus.ASSIGNED) {
            throw new BusinessRuleException(
                    "INSPECTION_NOT_EDITABLE",
                    "Only draft or assigned inspections can be edited"
            );
        }
        UUID previousTechnicianId = inspection.getTechnicianId();
        validateScheduledFor(request.scheduledFor());
        Client client = findClient(request.clientId());
        InspectionSite site = findSite(request.siteId());
        validateClientSite(client, site);
        client.ensureAvailableForScheduling();
        site.ensureAvailableForScheduling();
        Equipment equipment = validateEquipment(request.equipmentId(), site);
        User technician = request.technicianId() == null
                ? null
                : validateUser(request.technicianId(), UserRole.TECHNICIAN, "technicianId");
        validateSupervisor(request.supervisorId());
        InspectionTemplateVersion version = versionRepository.findById(inspection.getTemplateVersionId())
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "Inspection template version", inspection.getTemplateVersionId()
                ));
        validateEquipmentRequirement(version, equipment);

        inspection.updatePlanning(
                client.getId(),
                site.getId(),
                equipment == null ? null : equipment.getId(),
                technician == null ? null : technician.getId(),
                request.supervisorId(),
                request.title(),
                request.instructions(),
                request.priority(),
                request.scheduledFor()
        );
        Inspection saved = inspectionRepository().saveAndFlush(inspection);
        auditEventService.recordEvent(
                "INSPECTION_UPDATED",
                INSPECTION_ENTITY,
                saved.getId(),
                null,
                auditState(saved)
        );
        if (technician != null && !Objects.equals(previousTechnicianId, technician.getId())) {
            auditEventService.recordEvent(
                    "INSPECTION_ASSIGNED",
                    INSPECTION_ENTITY,
                    saved.getId(),
                    null,
                    "technicianId=" + technician.getId()
            );
        }
        return saved;
    }

    private Inspection findByIdForUpdate(UUID id) {
        return inspectionRepository().findWithSnapshotsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection", id));
    }

    private InspectionRepository inspectionRepository() {
        if (inspectionRepository == null) {
            throw new IllegalStateException("Inspection persistence is not available");
        }
        return inspectionRepository;
    }

    private Client findClient(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", id));
    }

    private InspectionSite findSite(UUID id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection site", id));
    }

    private void validateClientSite(Client client, InspectionSite site) {
        if (!client.getId().equals(site.getClient().getId())) {
            throw new BusinessRuleException(
                    "SITE_CLIENT_MISMATCH",
                    "The inspection site does not belong to the selected client",
                    List.of(new Violation("siteId", "Site must belong to clientId"))
            );
        }
    }

    private Equipment validateEquipment(UUID equipmentId, InspectionSite site) {
        if (equipmentId == null) {
            return null;
        }
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipment", equipmentId));
        if (!site.getId().equals(equipment.getSiteId())) {
            throw new BusinessRuleException(
                    "EQUIPMENT_SITE_MISMATCH",
                    "The equipment does not belong to the selected inspection site",
                    List.of(new Violation("equipmentId", "Equipment must belong to siteId"))
            );
        }
        if (equipment.getStatus() != EquipmentStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "EQUIPMENT_INACTIVE",
                    "Inactive equipment cannot be selected for new inspections"
            );
        }
        return equipment;
    }

    private User validateUser(UUID userId, UserRole requiredRole, String field) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getRole() == requiredRole)
                .orElseThrow(() -> new BusinessRuleException(
                        "INVALID_" + field.replace("Id", "").toUpperCase(Locale.ROOT),
                        "The selected " + field.replace("Id", "") + " must be an active "
                                + requiredRole.name().toLowerCase(Locale.ROOT),
                        List.of(new Violation(field, "User must be active and have role " + requiredRole))
                ));
    }

    private void validateSupervisor(UUID supervisorId) {
        if (supervisorId != null) {
            validateUser(supervisorId, UserRole.SUPERVISOR, "supervisorId");
        }
    }

    private void validateEquipmentRequirement(InspectionTemplateVersion version, Equipment equipment) {
        boolean siteInspection = isSiteInspection(version.getTemplate().getCategory());
        if (equipment == null && !siteInspection) {
            throw new BusinessRuleException(
                    "EQUIPMENT_REQUIRED",
                    "Equipment is required for this inspection template"
            );
        }
    }

    private boolean isSiteInspection(String category) {
        String normalized = Objects.requireNonNull(category, "template category must not be null")
                .trim().toLowerCase(Locale.ROOT);
        return normalized.equals("local")
                || normalized.equals("ambiente")
                || normalized.equals("site")
                || normalized.equals("location")
                || normalized.equals("environment");
    }

    private void validateScheduledFor(Instant scheduledFor) {
        if (scheduledFor == null) {
            throw new BusinessRuleException(
                    "SCHEDULED_FOR_REQUIRED",
                    "Scheduled date is required",
                    List.of(new Violation("scheduledFor", "must not be null"))
            );
        }
    }

    private String auditState(Inspection inspection) {
        return "status=" + inspection.getStatus()
                + ";clientId=" + inspection.getClientId()
                + ";siteId=" + inspection.getSiteId()
                + ";equipmentId=" + inspection.getEquipmentId()
                + ";technicianId=" + inspection.getTechnicianId();
    }
}
