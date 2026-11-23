package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.InspectionMobileQuery;
import com.fieldops.inspection.application.InspectionMobileService;
import com.fieldops.inspection.application.InspectionListQuery;
import com.fieldops.inspection.application.InspectionListService;
import com.fieldops.inspection.application.InspectionSchedulingService;
import com.fieldops.inspection.application.InspectionTransitionResult;
import com.fieldops.inspection.application.InspectionTransitionService;
import com.fieldops.inspection.application.InspectionResponseService;
import com.fieldops.inspection.application.InspectionEvidenceDownload;
import com.fieldops.inspection.application.InspectionEvidenceService;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.presentation.dto.AssignInspectionRequest;
import com.fieldops.inspection.presentation.dto.CancelInspectionRequest;
import com.fieldops.inspection.presentation.dto.CreateInspectionRequest;
import com.fieldops.inspection.presentation.dto.InspectionExecutionResponse;
import com.fieldops.inspection.presentation.dto.InspectionResponse;
import com.fieldops.inspection.presentation.dto.InspectionListResponse;
import com.fieldops.inspection.presentation.dto.InspectionTransitionResponse;
import com.fieldops.inspection.presentation.dto.InspectionResponseBatchRequest;
import com.fieldops.inspection.presentation.dto.InspectionResponseBatchResponse;
import com.fieldops.inspection.presentation.dto.InspectionResponseResponse;
import com.fieldops.inspection.presentation.dto.SaveInspectionResponseRequest;
import com.fieldops.inspection.presentation.dto.EvidenceMetadataRequest;
import com.fieldops.inspection.presentation.dto.EvidenceResponse;
import com.fieldops.inspection.presentation.dto.StartInspectionRequest;
import com.fieldops.inspection.presentation.dto.SubmitInspectionRequest;
import com.fieldops.inspection.presentation.dto.MobileInspectionResponse;
import com.fieldops.inspection.presentation.dto.MobileInspectionSummaryResponse;
import com.fieldops.inspection.presentation.dto.UpdateInspectionRequest;
import com.fieldops.inspection.presentation.mapper.InspectionMapper;
import com.fieldops.inspection.presentation.mapper.InspectionListMapper;
import com.fieldops.inspection.presentation.mapper.InspectionMobileMapper;
import com.fieldops.inspection.presentation.mapper.InspectionTransitionMapper;
import com.fieldops.inspection.presentation.mapper.InspectionResponseMapper;
import com.fieldops.inspection.presentation.mapper.InspectionEvidenceMapper;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
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
    private final ObjectProvider<InspectionMobileService> inspectionMobileServiceProvider;
    private final InspectionMobileMapper inspectionMobileMapper;
    private final InspectionTransitionService transitionService;
    private final InspectionTransitionMapper transitionMapper;
    private final ObjectProvider<InspectionResponseService> responseServiceProvider;
    private final InspectionResponseMapper responseMapper;
    private final ObjectProvider<InspectionEvidenceService> evidenceServiceProvider;
    private final InspectionEvidenceMapper evidenceMapper;

    public InspectionController(
            InspectionSchedulingService schedulingService,
            InspectionMapper inspectionMapper,
            ObjectProvider<InspectionListService> inspectionListServiceProvider,
            InspectionListMapper inspectionListMapper,
            ObjectProvider<InspectionMobileService> inspectionMobileServiceProvider,
            InspectionMobileMapper inspectionMobileMapper,
            InspectionTransitionService transitionService,
            InspectionTransitionMapper transitionMapper,
            ObjectProvider<InspectionResponseService> responseServiceProvider,
            InspectionResponseMapper responseMapper,
            ObjectProvider<InspectionEvidenceService> evidenceServiceProvider,
            InspectionEvidenceMapper evidenceMapper
    ) {
        this.schedulingService = schedulingService;
        this.inspectionMapper = inspectionMapper;
        this.inspectionListServiceProvider = inspectionListServiceProvider;
        this.inspectionListMapper = inspectionListMapper;
        this.inspectionMobileServiceProvider = inspectionMobileServiceProvider;
        this.inspectionMobileMapper = inspectionMobileMapper;
        this.transitionService = transitionService;
        this.transitionMapper = transitionMapper;
        this.responseServiceProvider = responseServiceProvider;
        this.responseMapper = responseMapper;
        this.evidenceServiceProvider = evidenceServiceProvider;
        this.evidenceMapper = evidenceMapper;
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
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List inspections for mobile technicians",
            description = "Returns only inspections assigned to the authenticated technician and supports incremental updates.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Mobile inspection list returned",
                            content = @Content(schema = @Schema(implementation = MobileInspectionSummaryResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public List<MobileInspectionSummaryResponse> listMobile(
            @RequestParam(required = false) InspectionStatus status,
            @RequestParam(required = false) Instant updatedSince
    ) {
        return inspectionMobileService().findAll(new InspectionMobileQuery(status, updatedSince)).stream()
                .map(inspectionMobileMapper::toSummary)
                .toList();
    }

    @GetMapping("/mobile/inspections/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get a mobile inspection execution payload",
            description = "Returns the assigned inspection, immutable checklist snapshot and related resources in one response.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Mobile inspection returned",
                            content = @Content(schema = @Schema(implementation = MobileInspectionResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "id": "3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5",
                                              "title": "Inspecao de Extintor - Planta Sul",
                                              "status": "ASSIGNED",
                                              "priority": "HIGH",
                                              "scheduledFor": "2026-08-12T13:00:00Z",
                                              "version": 3,
                                              "client": {"id": "6b1c6c9d-62e7-4ddf-a0d7-1dbd1b9b775a", "name": "Industria ABC"},
                                              "site": {"id": "95e33a13-cb27-443e-b2f6-90d3e8a0b9c9", "name": "Fabrica Sorocaba", "addressLine": "Rua A, 10", "latitude": -23.5, "longitude": -47.4},
                                              "equipment": {"id": "26d8c59a-11c2-4f12-b69c-6fb5d26e6cf4", "name": "Gerador GD-001", "qrCode": "QR-001"},
                                              "items": [],
                                              "responses": [],
                                              "review": null
                                            }
                                            """))),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not assigned to this technician",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public MobileInspectionResponse findMobile(@PathVariable UUID id) {
        return inspectionMobileMapper.toResponse(inspectionMobileService().findById(id));
    }

    private InspectionMobileService inspectionMobileService() {
        InspectionMobileService service = inspectionMobileServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Mobile inspection persistence is not available");
        }
        return service;
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

    @PostMapping("/inspections/{id}/start")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Start an inspection",
            description = "Starts an inspection assigned to the authenticated technician and records device/server timestamps.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inspection started",
                            content = @Content(schema = @Schema(implementation = InspectionExecutionResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "id": "3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5",
                                              "previousStatus": "ASSIGNED",
                                              "status": "IN_PROGRESS",
                                              "startedAtDevice": "2026-08-01T13:02:00Z",
                                              "startedAtServer": "2026-08-01T16:02:04Z",
                                              "startPosition": {"latitude": -23.5, "longitude": -47.4, "accuracy": 12.5, "capturedAt": "2026-08-01T13:02:03Z"}
                                            }
                                            """))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection is not assigned to this technician",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "409", description = "Invalid inspection transition",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionExecutionResponse start(
            @PathVariable UUID id,
            @Valid @RequestBody StartInspectionRequest request
    ) {
        InspectionTransitionResult result = transitionService.start(id, request);
        return transitionMapper.toExecutionResponse(result.inspection(), result.previousStatus());
    }

    @PostMapping("/inspections/{id}/submit")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Submit an inspection",
            description = "Validates all required snapshot items server-side, then records completion and server timestamps.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inspection submitted",
                            content = @Content(schema = @Schema(implementation = InspectionExecutionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection is not assigned to this technician",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "409", description = "Invalid inspection transition",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "422", description = "Required responses, observations or evidence are missing",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionExecutionResponse submit(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitInspectionRequest request
    ) {
        InspectionTransitionResult result = transitionService.submit(id, request);
        return transitionMapper.toExecutionResponse(result.inspection(), result.previousStatus());
    }

    @GetMapping("/inspections/{inspectionId}/responses")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List inspection responses",
            description = "Returns the current typed response for each answered snapshot item.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Responses returned",
                            content = @Content(schema = @Schema(implementation = InspectionResponseResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection is not assigned to this technician",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public List<InspectionResponseResponse> listResponses(@PathVariable UUID inspectionId) {
        return responseService().findAll(inspectionId);
    }

    @PutMapping("/inspections/{inspectionId}/responses/{responseId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Create or update an inspection response",
            description = "Upserts a typed response by the device-generated response id.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Response applied",
                            content = @Content(schema = @Schema(implementation = InspectionResponseResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "409", description = "Response conflicts with an existing item",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "422", description = "Response value does not match the snapshot type",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionResponseResponse upsertResponse(
            @PathVariable UUID inspectionId,
            @PathVariable UUID responseId,
            @Valid @RequestBody SaveInspectionResponseRequest request
    ) {
        return responseService().upsert(inspectionId, responseId, request);
    }

    @PostMapping("/inspections/{inspectionId}/responses:batch")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Apply inspection responses in batch",
            description = "Processes every response independently and returns an individual result for each item.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Batch processed",
                            content = @Content(schema = @Schema(implementation = InspectionResponseBatchResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid batch envelope",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "422", description = "Inspection responses are locked",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionResponseBatchResponse batchResponses(
            @PathVariable UUID inspectionId,
            @Valid @RequestBody InspectionResponseBatchRequest request
    ) {
        return responseService().batch(inspectionId, request);
    }

    private InspectionResponseService responseService() {
        InspectionResponseService service = responseServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Inspection response persistence is not available");
        }
        return service;
    }

    @PostMapping(value = "/inspections/{inspectionId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload private inspection evidence",
            description = "Stores image metadata in the API and the bytes in private storage.")
    public EvidenceResponse uploadEvidence(
            @PathVariable UUID inspectionId,
            @Valid @RequestPart("metadata") EvidenceMetadataRequest metadata,
            @RequestPart("file") MultipartFile file
    ) {
        return evidenceMapper.toResponse(evidenceService().upload(inspectionId, metadata, file));
    }

    @GetMapping("/inspections/{inspectionId}/evidence")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EVIDENCE_READ)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List inspection evidence",
            description = "Lists active evidence visible to the technician or supervisor in scope.")
    public List<EvidenceResponse> listEvidence(@PathVariable UUID inspectionId) {
        return evidenceService().findAll(inspectionId).stream().map(evidenceMapper::toResponse).toList();
    }

    @GetMapping("/evidence/{id}")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EVIDENCE_READ)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Download private evidence",
            description = "Streams evidence only after server-side scope validation.")
    public ResponseEntity<Resource> downloadEvidence(@PathVariable UUID id) {
        InspectionEvidenceDownload download = evidenceService().download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.evidence().getMimeType()))
                .contentLength(download.content().length)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ByteArrayResource(download.content()));
    }

    @DeleteMapping("/evidence/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete evidence",
            description = "Soft-deletes evidence, removes the private object and records an audit event.")
    public void deleteEvidence(@PathVariable UUID id) {
        evidenceService().delete(id);
    }

    private InspectionEvidenceService evidenceService() {
        InspectionEvidenceService service = evidenceServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Inspection evidence persistence is not available");
        }
        return service;
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

}
