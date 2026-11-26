package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.InspectionCsvExportService;
import com.fieldops.inspection.application.InspectionListQuery;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.domain.model.NonConformityStatus;
import com.fieldops.shared.presentation.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "CSV exports", description = "Streaming CSV exports for list views.")
@SecurityRequirement(name = "bearerAuth")
public class InspectionCsvExportController {

    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    private final InspectionCsvExportService exportService;

    public InspectionCsvExportController(InspectionCsvExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping(value = "/inspections/export.csv", produces = "text/csv")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @Operation(
            summary = "Export filtered inspections as CSV",
            description = "Streams the same inspection filters as the administrative list. The file uses UTF-8 with BOM and semicolon separator.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inspection CSV streamed",
                            content = @Content(mediaType = CSV_CONTENT_TYPE)),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public void exportInspections(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InspectionStatus status,
            @RequestParam(required = false) UUID technicianId,
            @RequestParam(required = false) UUID supervisorId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) InspectionPriority priority,
            @RequestParam(required = false) Instant scheduledFrom,
            @RequestParam(required = false) Instant scheduledTo,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "scheduledFor,asc") String sort,
            HttpServletResponse response
    ) throws IOException {
        configure(response, "inspections.csv");
        exportService.writeInspections(new InspectionListQuery(
                search, status, technicianId, supervisorId, clientId, siteId, equipmentId,
                priority, scheduledFrom, scheduledTo, overdue, 0, 1, sort
        ), response.getOutputStream());
    }

    @GetMapping(value = "/non-conformities/export.csv", produces = "text/csv")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_NON_CONFORMITY_READ)
    @Operation(
            summary = "Export filtered non-conformities as CSV",
            description = "Streams the same filters as the non-conformity list. The file uses UTF-8 with BOM and semicolon separator.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Non-conformity CSV streamed",
                            content = @Content(mediaType = CSV_CONTENT_TYPE)),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public void exportNonConformities(
            @RequestParam(required = false) UUID inspectionId,
            @RequestParam(required = false) NonConformitySeverity severity,
            @RequestParam(required = false) NonConformityStatus status,
            @RequestParam(required = false) UUID clientId,
            HttpServletResponse response
    ) throws IOException {
        configure(response, "non-conformities.csv");
        exportService.writeNonConformities(inspectionId, severity, status, clientId, response.getOutputStream());
    }

    private void configure(HttpServletResponse response, String filename) {
        response.setContentType(CSV_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        response.setHeader("Cache-Control", "no-store");
    }
}
