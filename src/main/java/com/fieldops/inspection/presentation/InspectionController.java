package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.InspectionTransitionResult;
import com.fieldops.inspection.application.InspectionTransitionService;
import com.fieldops.inspection.presentation.dto.AssignInspectionRequest;
import com.fieldops.inspection.presentation.dto.CancelInspectionRequest;
import com.fieldops.inspection.presentation.dto.InspectionSummaryResponse;
import com.fieldops.inspection.presentation.dto.InspectionTransitionResponse;
import com.fieldops.inspection.presentation.mapper.InspectionTransitionMapper;
import com.fieldops.shared.presentation.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inspections", description = "Inspection list contract endpoints for dashboard and mobile.")
public class InspectionController {

    private final InspectionTransitionService transitionService;
    private final InspectionTransitionMapper transitionMapper;

    public InspectionController(
            InspectionTransitionService transitionService,
            InspectionTransitionMapper transitionMapper
    ) {
        this.transitionService = transitionService;
        this.transitionMapper = transitionMapper;
    }

    @GetMapping("/inspections")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List inspections for dashboard",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Inspection list returned",
                            content = @Content(
                                    schema = @Schema(implementation = InspectionSummaryResponse.class),
                                    examples = @ExampleObject(value = """
                                            [
                                              {
                                                "id": "3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5",
                                                "title": "Inspecao de Extintor - Planta Sul",
                                                "status": "IN_PROGRESS",
                                                "scheduledAt": "2026-09-14T10:00:00Z"
                                              }
                                            ]
                                            """)
                            )
                    ),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public List<InspectionSummaryResponse> list() {
        return mockInspectionList();
    }

    @GetMapping("/mobile/inspections")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List inspections for mobile technicians",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Mobile inspection list returned",
                            content = @Content(schema = @Schema(implementation = InspectionSummaryResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public List<InspectionSummaryResponse> listMobile() {
        return mockInspectionList();
    }

    @PostMapping("/inspections/{id}/assign")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Assign an inspection to an active technician",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inspection assigned",
                            content = @Content(schema = @Schema(implementation = InspectionTransitionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection or technician not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "409", description = "Invalid inspection transition",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "422", description = "Technician is not eligible",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionTransitionResponse assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignInspectionRequest request
    ) {
        InspectionTransitionResult result = transitionService.assign(id, request.technicianId());
        return transitionMapper.toResponse(result.inspection(), result.previousStatus());
    }

    @PostMapping("/inspections/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Cancel an inspection with a business reason",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inspection canceled",
                            content = @Content(schema = @Schema(implementation = InspectionTransitionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Cancellation reason is missing",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "409", description = "Invalid inspection transition",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionTransitionResponse cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelInspectionRequest request
    ) {
        InspectionTransitionResult result = transitionService.cancel(id, request.reason());
        return transitionMapper.toResponse(result.inspection(), result.previousStatus());
    }

    private List<InspectionSummaryResponse> mockInspectionList() {
        return List.of(new InspectionSummaryResponse(
                UUID.fromString("3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5"),
                "Inspecao de Extintor - Planta Sul",
                "IN_PROGRESS",
                OffsetDateTime.parse("2026-09-14T10:00:00Z")
        ));
    }
}
