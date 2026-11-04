package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.InspectionSchedulingService;
import com.fieldops.inspection.presentation.dto.CreateInspectionRequest;
import com.fieldops.inspection.presentation.dto.InspectionResponse;
import com.fieldops.inspection.presentation.dto.InspectionSummaryResponse;
import com.fieldops.inspection.presentation.dto.UpdateInspectionRequest;
import com.fieldops.inspection.presentation.mapper.InspectionMapper;
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
import org.springframework.web.bind.annotation.PutMapping;
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

    private final InspectionSchedulingService schedulingService;
    private final InspectionMapper inspectionMapper;

    public InspectionController(
            InspectionSchedulingService schedulingService,
            InspectionMapper inspectionMapper
    ) {
        this.schedulingService = schedulingService;
        this.inspectionMapper = inspectionMapper;
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

    @PostMapping("/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @Operation(
            summary = "Schedule an inspection",
            description = "Validates all links, creates the inspection and freezes the published checklist in one transaction.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Inspection scheduled",
                            content = @Content(schema = @Schema(implementation = InspectionResponse.class), examples = @ExampleObject(value = """
                                    {
                                      "id": "3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5",
                                      "status": "ASSIGNED",
                                      "scheduledFor": "2026-11-10T13:00:00Z",
                                      "itemSnapshots": [{"sectionTitle": "Condicoes", "itemTitle": "Lacre intacto?", "sectionOrder": 1, "itemOrder": 1}]
                                    }
                                    """))),
                    @ApiResponse(responseCode = "400", description = "Invalid payload",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "422", description = "Link or template validation failed",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionResponse create(@Valid @RequestBody CreateInspectionRequest request) {
        return inspectionMapper.toResponse(schedulingService.schedule(request));
    }

    @GetMapping("/inspections/{id}")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @Operation(
            summary = "Get a scheduled inspection",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inspection returned",
                            content = @Content(schema = @Schema(implementation = InspectionResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionResponse findById(@PathVariable UUID id) {
        return inspectionMapper.toResponse(schedulingService.findById(id));
    }

    @PutMapping("/inspections/{id}")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @Operation(
            summary = "Update inspection planning data",
            description = "Updates links and planning fields before execution without changing the frozen checklist snapshot.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inspection updated",
                            content = @Content(schema = @Schema(implementation = InspectionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid payload",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "422", description = "Inspection is not editable or link validation failed",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInspectionRequest request
    ) {
        return inspectionMapper.toResponse(schedulingService.update(id, request));
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
