package com.fieldops.dashboard.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.dashboard.infrastructure.persistence.InspectionStatusCountProjection;
import com.fieldops.dashboard.infrastructure.persistence.NonConformitySeverityCountProjection;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final NonConformityRepository nonConformityRepository = mock(NonConformityRepository.class);
    private final AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
    private final Authentication authentication = mock(Authentication.class);
    private final DashboardService service = new DashboardService(
            inspectionRepository, nonConformityRepository, userResolver
    );

    private final Instant periodStart = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant periodEnd = Instant.parse("2026-01-31T23:59:59Z");

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        InspectionStatusCountProjection assigned = status(InspectionStatus.ASSIGNED, 2);
        InspectionStatusCountProjection underReview = status(InspectionStatus.UNDER_REVIEW, 1);
        NonConformitySeverityCountProjection high = severity(NonConformitySeverity.HIGH, 2);
        when(inspectionRepository.countByStatusForDashboard(any(), any(), any()))
                .thenReturn(List.of(assigned, underReview));
        when(inspectionRepository.countForDashboard(any(), any(), any())).thenReturn(3L);
        when(inspectionRepository.countOverdueForDashboard(any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(nonConformityRepository.countBySeverityForDashboard(any(), any(), any()))
                .thenReturn(List.of(high));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorGetsAggregatesWithoutSupervisorRestriction() {
        User admin = user(UserRole.ADMIN);
        when(userResolver.requireUser(authentication)).thenReturn(admin);

        DashboardSummary result = service.summary(periodStart, periodEnd);

        assertThat(result.totalInspections()).isEqualTo(3);
        assertThat(result.overdueInspections()).isEqualTo(1);
        assertThat(result.inspectionsAwaitingReview()).isEqualTo(1);
        assertThat(result.inspectionsByStatus())
                .filteredOn(value -> value.count() > 0)
                .extracting(DashboardInspectionStatusCount::status, DashboardInspectionStatusCount::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(InspectionStatus.ASSIGNED, 2L),
                        org.assertj.core.groups.Tuple.tuple(InspectionStatus.UNDER_REVIEW, 1L)
                );
        assertThat(result.nonConformitiesBySeverity())
                .filteredOn(value -> value.count() > 0)
                .extracting(DashboardNonConformitySeverityCount::severity, DashboardNonConformitySeverityCount::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(NonConformitySeverity.HIGH, 2L));

        verify(inspectionRepository).countForDashboard(periodStart, periodEnd, null);
        verify(inspectionRepository).countByStatusForDashboard(periodStart, periodEnd, null);
        verify(nonConformityRepository).countBySeverityForDashboard(periodStart, periodEnd, null);
    }

    @Test
    void supervisorQueriesOnlyTheirOwnInspectionScope() {
        UUID supervisorId = UUID.randomUUID();
        User supervisor = user(UserRole.SUPERVISOR);
        ReflectionTestUtils.setField(supervisor, "id", supervisorId);
        when(userResolver.requireUser(authentication)).thenReturn(supervisor);

        service.inspectionsByStatus(periodStart, periodEnd);

        verify(inspectionRepository).countByStatusForDashboard(periodStart, periodEnd, supervisorId);
    }

    @Test
    void rejectsAnInvertedPeriodBeforeQueryingDatabase() {
        when(userResolver.requireUser(authentication)).thenReturn(user(UserRole.ADMIN));

        assertThatThrownBy(() -> service.summary(periodEnd, periodStart))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("period start");
    }

    private InspectionStatusCountProjection status(InspectionStatus value, long count) {
        InspectionStatusCountProjection projection = mock(InspectionStatusCountProjection.class);
        when(projection.getStatus()).thenReturn(value);
        when(projection.getCount()).thenReturn(count);
        return projection;
    }

    private NonConformitySeverityCountProjection severity(NonConformitySeverity value, long count) {
        NonConformitySeverityCountProjection projection = mock(NonConformitySeverityCountProjection.class);
        when(projection.getSeverity()).thenReturn(value);
        when(projection.getCount()).thenReturn(count);
        return projection;
    }

    private User user(UserRole role) {
        return User.create("Dashboard User", UUID.randomUUID() + "@fieldops.local", "hash", role,
                UserStatus.ACTIVE, null);
    }
}
