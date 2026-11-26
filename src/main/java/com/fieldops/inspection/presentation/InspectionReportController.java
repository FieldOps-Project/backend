package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.InspectionReportService;
import com.fieldops.shared.presentation.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections")
@Tag(name = "Inspection reports", description = "PDF exports of completed inspection snapshots.")
public class InspectionReportController {

    private final InspectionReportService reportService;

    public InspectionReportController(InspectionReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping(value = "/{id}/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Export an inspection report as PDF",
            description = "Exports the immutable checklist snapshot, answers, evidence images, non-conformities and latest review decision. Access follows the inspection scope.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "PDF report generated",
                            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not found in the authenticated scope",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        byte[] pdf = reportService.generate(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inspection-" + id + ".pdf")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }
}
