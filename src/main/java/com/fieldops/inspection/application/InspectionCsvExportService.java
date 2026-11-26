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
import com.fieldops.inspection.domain.model.NonConformity;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.domain.model.NonConformityStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionItemSnapshotRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionSpecifications;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class InspectionCsvExportService {

    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String SEPARATOR = ";";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "title", "priority", "status", "scheduledFor", "createdAt", "updatedAt"
    );

    private final InspectionRepository inspectionRepository;
    private final InspectionItemSnapshotRepository snapshotRepository;
    private final InspectionResponseRepository responseRepository;
    private final ClientRepository clientRepository;
    private final InspectionSiteRepository siteRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final NonConformityRepository nonConformityRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public InspectionCsvExportService(
            InspectionRepository inspectionRepository,
            InspectionItemSnapshotRepository snapshotRepository,
            InspectionResponseRepository responseRepository,
            ClientRepository clientRepository,
            InspectionSiteRepository siteRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            NonConformityRepository nonConformityRepository,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepository = inspectionRepository;
        this.snapshotRepository = snapshotRepository;
        this.responseRepository = responseRepository;
        this.clientRepository = clientRepository;
        this.siteRepository = siteRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.nonConformityRepository = nonConformityRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public void writeInspections(InspectionListQuery query, OutputStream output) throws IOException {
        User user = authenticatedUserResolver.requireUser(SecurityContextHolder.getContext().getAuthentication());
        UUID scopedSupervisorId = user.getRole() == UserRole.SUPERVISOR ? user.getId() : query.supervisorId();
        Instant now = Instant.now();
        InspectionListQuery exportQuery = new InspectionListQuery(
                query.search(), query.status(), query.technicianId(), query.supervisorId(), query.clientId(),
                query.siteId(), query.equipmentId(), query.priority(), query.scheduledFrom(), query.scheduledTo(),
                query.overdue(), 0, 1, query.sort()
        );
        CsvWriter csv = new CsvWriter(output);
        csv.header("id", "title", "client_id", "client_name", "site_id", "site_name", "equipment_id",
                "equipment_name", "technician_id", "technician_name", "priority", "status", "scheduled_for",
                "answered_items", "applicable_items", "progress", "updated_at", "overdue");
        try (Stream<Inspection> inspections = inspectionRepository.findBy(
                InspectionSpecifications.withFilters(exportQuery, scopedSupervisorId, now),
                queryBuilder -> queryBuilder.sortBy(parseSort(query.sort())).stream()
        )) {
            inspections.forEach(inspection -> {
                try {
                    writeInspection(csv, inspection, now);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
        csv.flush();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_NON_CONFORMITY_READ)
    public void writeNonConformities(
            UUID inspectionId,
            NonConformitySeverity severity,
            NonConformityStatus status,
            UUID clientId,
            OutputStream output
    ) throws IOException {
        User user = authenticatedUserResolver.requireUser(SecurityContextHolder.getContext().getAuthentication());
        UUID technicianId = user.getRole() == UserRole.TECHNICIAN ? user.getId() : null;
        UUID supervisorId = user.getRole() == UserRole.SUPERVISOR ? user.getId() : null;
        if (technicianId == null && supervisorId == null) {
            throw new InvalidRequestException("EXPORT_SCOPE_UNSUPPORTED", "The authenticated role cannot export non-conformities");
        }

        CsvWriter csv = new CsvWriter(output);
        csv.header("id", "inspection_id", "inspection_item_id", "response_id", "title", "description",
                "severity", "status", "created_at_device", "server_received_at", "created_at", "updated_at", "version");
        try (Stream<NonConformity> nonConformities = nonConformityRepository.streamForExport(
                inspectionId, severity, status, clientId, technicianId, supervisorId
        )) {
            nonConformities.forEach(nonConformity -> {
                try {
                    csv.row(
                            nonConformity.getId(), nonConformity.getInspectionId(), nonConformity.getInspectionItemId(),
                            nonConformity.getResponseId(), nonConformity.getTitle(), nonConformity.getDescription(),
                            nonConformity.getSeverity(), nonConformity.getStatus(), nonConformity.getCreatedAtDevice(),
                            nonConformity.getServerReceivedAt(), nonConformity.getCreatedAt(), nonConformity.getUpdatedAt(),
                            nonConformity.getVersion()
                    );
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
        csv.flush();
    }

    private void writeInspection(CsvWriter csv, Inspection inspection, Instant now) throws IOException {
        Client client = clientRepository.findById(inspection.getClientId()).orElse(null);
        InspectionSite site = siteRepository.findById(inspection.getSiteId()).orElse(null);
        Equipment equipment = inspection.getEquipmentId() == null
                ? null : equipmentRepository.findById(inspection.getEquipmentId()).orElse(null);
        User technician = inspection.getTechnicianId() == null
                ? null : userRepository.findById(inspection.getTechnicianId()).orElse(null);
        int applicableItems = Math.toIntExact(snapshotRepository.countApplicableItemsByInspectionId(inspection.getId()));
        int answeredItems = Math.toIntExact(responseRepository.countByInspectionId(inspection.getId()));
        int progress = applicableItems == 0 ? 0 : Math.min(100, answeredItems * 100 / applicableItems);
        boolean overdue = inspection.getScheduledFor().isBefore(now)
                && (inspection.getStatus() == InspectionStatus.ASSIGNED
                || inspection.getStatus() == InspectionStatus.IN_PROGRESS);
        csv.row(
                inspection.getId(), inspection.getTitle(), inspection.getClientId(), nameOf(client), inspection.getSiteId(),
                nameOf(site), inspection.getEquipmentId(), nameOf(equipment), inspection.getTechnicianId(), nameOf(technician),
                inspection.getPriority(), inspection.getStatus(), inspection.getScheduledFor(), answeredItems,
                applicableItems, progress, inspection.getUpdatedAt(), overdue
        );
    }

    private String nameOf(Client value) { return value == null ? null : value.getName(); }
    private String nameOf(InspectionSite value) { return value == null ? null : value.getName(); }
    private String nameOf(Equipment value) { return value == null ? null : value.getName(); }
    private String nameOf(User value) { return value == null ? null : value.getName(); }

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
            } catch (IllegalArgumentException exception) {
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

    private static final class CsvWriter {
        private final OutputStream output;

        private CsvWriter(OutputStream output) throws IOException {
            this.output = output;
            output.write(UTF_8_BOM);
        }

        private void header(String... values) throws IOException {
            row(values);
        }

        private void row(Object... values) throws IOException {
            for (int index = 0; index < values.length; index++) {
                if (index > 0) output.write(SEPARATOR.getBytes(StandardCharsets.UTF_8));
                output.write(escape(values[index] == null ? "" : values[index].toString()).getBytes(StandardCharsets.UTF_8));
            }
            output.write('\n');
        }

        private String escape(String value) {
            if (value.indexOf(';') < 0 && value.indexOf('"') < 0
                    && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
                return value;
            }
            return '"' + value.replace("\"", "\"\"") + '"';
        }

        private void flush() throws IOException {
            output.flush();
        }
    }
}
