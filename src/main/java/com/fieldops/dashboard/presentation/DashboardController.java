package com.fieldops.dashboard.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.dashboard.application.DashboardInspectionStatusCount;
import com.fieldops.dashboard.application.DashboardNonConformitySeverityCount;
import com.fieldops.dashboard.application.DashboardService;
import com.fieldops.dashboard.application.DashboardSummary;
import com.fieldops.dashboard.presentation.dto.DashboardSummaryResponse;
import com.fieldops.dashboard.presentation.dto.InspectionStatusCountResponse;
import com.fieldops.dashboard.presentation.dto.NonConformitySeverityCountResponse;
import com.fieldops.shared.presentation.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Administrative inspection indicators.")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @Operation(
            summary = "Get dashboard indicators",
            description = "Returns status totals, overdue inspections, inspections awaiting review and non-conformities by severity. "
                    + "The period filters scheduled inspections; supervisors see only their own scope.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dashboard indicators returned",
                            content = @Content(schema = @Schema(implementation = DashboardSummaryResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid period",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public DashboardSummaryResponse summary(
            @RequestParam(required = false, name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false, name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return toResponse(dashboardService.summary(from, to));
    }

    @GetMapping("/inspections-by-status")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @Operation(
            summary = "Count inspections by status",
            description = "Returns database-aggregated inspection counts by status for the selected scheduled period.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Status counts returned",
                            content = @Content(array = @ArraySchema(
                                    schema = @Schema(implementation = InspectionStatusCountResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "Invalid period",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public List<InspectionStatusCountResponse> inspectionsByStatus(
            @RequestParam(required = false, name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false, name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return dashboardService.inspectionsByStatus(from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/non-conformities-by-severity")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @Operation(
            summary = "Count non-conformities by severity",
            description = "Returns database-aggregated non-conformity counts by severity for the selected scheduled period.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Severity counts returned",
                            content = @Content(array = @ArraySchema(
                                    schema = @Schema(implementation = NonConformitySeverityCountResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "Invalid period",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public List<NonConformitySeverityCountResponse> nonConformitiesBySeverity(
            @RequestParam(required = false, name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false, name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return dashboardService.nonConformitiesBySeverity(from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    private DashboardSummaryResponse toResponse(DashboardSummary summary) {
        return new DashboardSummaryResponse(
                summary.totalInspections(),
                summary.overdueInspections(),
                summary.inspectionsAwaitingReview(),
                summary.inspectionsByStatus().stream().map(this::toResponse).toList(),
                summary.nonConformitiesBySeverity().stream().map(this::toResponse).toList()
        );
    }

    private InspectionStatusCountResponse toResponse(DashboardInspectionStatusCount count) {
        return new InspectionStatusCountResponse(count.status(), count.count());
    }

    private NonConformitySeverityCountResponse toResponse(DashboardNonConformitySeverityCount count) {
        return new NonConformitySeverityCountResponse(count.severity(), count.count());
    }
}
