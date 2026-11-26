package com.fieldops.dashboard.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final List<InspectionStatus> OVERDUE_STATUSES = List.of(
            InspectionStatus.ASSIGNED,
            InspectionStatus.IN_PROGRESS
    );

    private final InspectionRepository inspectionRepository;
    private final NonConformityRepository nonConformityRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final Clock clock;

    public DashboardService(
            InspectionRepository inspectionRepository,
            NonConformityRepository nonConformityRepository,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inspectionRepository = inspectionRepository;
        this.nonConformityRepository = nonConformityRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public DashboardSummary summary(Instant periodStart, Instant periodEnd) {
        validatePeriod(periodStart, periodEnd);
        UUID supervisorId = supervisorScope();
        Map<InspectionStatus, Long> statusCounts = statusCounts(periodStart, periodEnd, supervisorId);
        Map<NonConformitySeverity, Long> severityCounts = severityCounts(periodStart, periodEnd, supervisorId);
        long total = inspectionRepository.countForDashboard(periodStart, periodEnd, supervisorId);
        long overdue = inspectionRepository.countOverdueForDashboard(
                periodStart, periodEnd, supervisorId, clock.instant(), OVERDUE_STATUSES
        );
        return new DashboardSummary(
                total,
                overdue,
                statusCounts.getOrDefault(InspectionStatus.UNDER_REVIEW, 0L),
                allInspectionStatuses(statusCounts),
                allNonConformitySeverities(severityCounts)
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public List<DashboardInspectionStatusCount> inspectionsByStatus(Instant periodStart, Instant periodEnd) {
        validatePeriod(periodStart, periodEnd);
        return allInspectionStatuses(statusCounts(periodStart, periodEnd, supervisorScope()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public List<DashboardNonConformitySeverityCount> nonConformitiesBySeverity(
            Instant periodStart,
            Instant periodEnd
    ) {
        validatePeriod(periodStart, periodEnd);
        return allNonConformitySeverities(severityCounts(periodStart, periodEnd, supervisorScope()));
    }

    private Map<InspectionStatus, Long> statusCounts(Instant periodStart, Instant periodEnd, UUID supervisorId) {
        return inspectionRepository.countByStatusForDashboard(periodStart, periodEnd, supervisorId).stream()
                .collect(Collectors.toMap(
                        projection -> projection.getStatus(),
                        projection -> projection.getCount(),
                        Long::sum,
                        () -> new EnumMap<>(InspectionStatus.class)
                ));
    }

    private Map<NonConformitySeverity, Long> severityCounts(
            Instant periodStart,
            Instant periodEnd,
            UUID supervisorId
    ) {
        return nonConformityRepository.countBySeverityForDashboard(periodStart, periodEnd, supervisorId).stream()
                .collect(Collectors.toMap(
                        projection -> projection.getSeverity(),
                        projection -> projection.getCount(),
                        Long::sum,
                        () -> new EnumMap<>(NonConformitySeverity.class)
                ));
    }

    private List<DashboardInspectionStatusCount> allInspectionStatuses(Map<InspectionStatus, Long> counts) {
        return List.of(InspectionStatus.values()).stream()
                .map(status -> new DashboardInspectionStatusCount(status, counts.getOrDefault(status, 0L)))
                .toList();
    }

    private List<DashboardNonConformitySeverityCount> allNonConformitySeverities(
            Map<NonConformitySeverity, Long> counts
    ) {
        return List.of(NonConformitySeverity.values()).stream()
                .map(severity -> new DashboardNonConformitySeverityCount(
                        severity, counts.getOrDefault(severity, 0L)
                ))
                .toList();
    }

    private UUID supervisorScope() {
        User user = authenticatedUserResolver.requireUser(
                SecurityContextHolder.getContext().getAuthentication()
        );
        return user.getRole() == UserRole.SUPERVISOR ? user.getId() : null;
    }

    private void validatePeriod(Instant periodStart, Instant periodEnd) {
        if (periodStart != null && periodEnd != null && periodStart.isAfter(periodEnd)) {
            throw new InvalidRequestException(
                    "INVALID_PERIOD",
                    "The period start must be before or equal to the period end"
            );
        }
    }
}
