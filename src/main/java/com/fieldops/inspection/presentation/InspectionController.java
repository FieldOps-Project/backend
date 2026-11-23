package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.InspectionListQuery;
import com.fieldops.inspection.application.InspectionListService;
import com.fieldops.inspection.application.InspectionSchedulingService;
import com.fieldops.inspection.application.InspectionTransitionResult;
import com.fieldops.inspection.application.InspectionTransitionService;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.presentation.dto.AssignInspectionRequest;
import com.fieldops.inspection.presentation.dto.CancelInspectionRequest;
import com.fieldops.inspection.presentation.dto.CreateInspectionRequest;
import com.fieldops.inspection.presentation.dto.InspectionResponse;
import com.fieldops.inspection.presentation.dto.InspectionListResponse;
import com.fieldops.inspection.presentation.dto.InspectionSummaryResponse;
import com.fieldops.inspection.presentation.dto.InspectionTransitionResponse;
import com.fieldops.inspection.presentation.dto.UpdateInspectionRequest;
import com.fieldops.inspection.presentation.mapper.InspectionMapper;
import com.fieldops.inspection.presentation.mapper.InspectionListMapper;
import com.fieldops.inspection.presentation.mapper.InspectionTransitionMapper;
import com.fieldops.shared.presentation.dto.ApiError;
import com.fieldops.shared.presentation.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inspections", description = "Inspection list contract endpoints for dashboard and mobile.")
public class InspectionController {

    private final InspectionSchedulingService schedulingService;
    private final InspectionMapper inspectionMapper;
    private final ObjectProvider<InspectionListService> inspectionListServiceProvider;
    private final InspectionListMapper inspectionListMapper;
    private final InspectionTransitionService transitionService;
    private final InspectionTransitionMapper transitionMapper;

    public InspectionController(
            InspectionSchedulingService schedulingService,
            InspectionMapper inspectionMapper,
            ObjectProvider<InspectionListService> inspectionListServiceProvider,
            InspectionListMapper inspectionListMapper,
            InspectionTransitionService transitionService,
            InspectionTransitionMapper transitionMapper
    ) {
        this.schedulingService = schedulingService;
        this.inspectionMapper = inspectionMapper;
        this.inspectionListServiceProvider = inspectionListServiceProvider;
        this.inspectionListMapper = inspectionListMapper;
        this.transitionService = transitionService;
        this.transitionMapper = transitionMapper;
    }

    @GetMapping("/inspections")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List inspections for the administrative dashboard",
            description = "Lists inspections with filters, bounded pagination, scope enforcement and derived overdue status.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Paginated inspection list returned",
                            content = @Content(
                                    schema = @Schema(implementation = InspectionListResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "content": [{
                                                "id": "3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5",
                                                "title": "Inspecao de Extintor - Planta Sul",
                                                "clientName": "Cliente Sul",
                                                "siteName": "Planta Sul",
                                                "equipmentName": "Extintor 01",
                                                "technicianName": "Maria Silva",
                                                "priority": "HIGH",
                                                "status": "IN_PROGRESS",
                                                "scheduledFor": "2026-09-14T10:00:00Z",
                                                "answeredItems": 3,
                                                "applicableItems": 5,
                                                "progress": 60,
                                                "updatedAt": "2026-09-14T12:00:00Z",
                                                "overdue": false
                                              }],
                                              "page": 0,
                                              "size": 20,
                                              "totalElements": 1,
                                              "totalPages": 1,
                                              "first": true,
                                              "last": true
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid filter or sort",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public PageResponse<InspectionListResponse> list(
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "scheduledFor,asc") String sort
    ) {
        return PageResponse.from(inspectionListService().findAll(new InspectionListQuery(
                search,
                status,
                technicianId,
                supervisorId,
                clientId,
                siteId,
                equipmentId,
                priority,
                scheduledFrom,
                scheduledTo,
                overdue,
                page,
                size,
                sort
        )).map(inspectionListMapper::toResponse));
    }

    private InspectionListService inspectionListService() {
        InspectionListService service = inspectionListServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Inspection list persistence is not available");
        }
        return service;
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
