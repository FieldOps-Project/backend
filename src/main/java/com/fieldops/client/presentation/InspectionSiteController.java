package com.fieldops.client.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.client.application.InspectionSiteService;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import com.fieldops.client.presentation.dto.CreateInspectionSiteRequest;
import com.fieldops.client.presentation.dto.InspectionSiteResponse;
import com.fieldops.client.presentation.dto.UpdateInspectionSiteRequest;
import com.fieldops.client.presentation.dto.UpdateInspectionSiteStatusRequest;
import com.fieldops.client.presentation.mapper.InspectionSiteMapper;
import com.fieldops.shared.presentation.dto.ApiError;
import com.fieldops.shared.presentation.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inspection sites", description = "Inspection site management endpoints.")
public class InspectionSiteController {

    private final InspectionSiteService inspectionSiteService;
    private final InspectionSiteMapper inspectionSiteMapper;

    public InspectionSiteController(
            InspectionSiteService inspectionSiteService,
            InspectionSiteMapper inspectionSiteMapper
    ) {
        this.inspectionSiteService = inspectionSiteService;
        this.inspectionSiteMapper = inspectionSiteMapper;
    }

    @GetMapping("/sites")
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "List inspection sites",
            description = "Lists sites globally or filtered by client, with optional text/status filters and bounded pagination.",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Paginated site list",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [{
                                        "id": "7ae3e623-419b-4adf-8da4-7f9bd7c3dd35",
                                        "clientId": "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed",
                                        "name": "Unidade Sorocaba",
                                        "city": "Sorocaba",
                                        "state": "SP",
                                        "status": "ACTIVE",
                                        "version": 0
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
            )
    )
    public PageResponse<InspectionSiteResponse> findAll(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InspectionSiteStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return toPageResponse(inspectionSiteService.findAll(clientId, search, status, page, size, sort));
    }

    @GetMapping("/clients/{clientId}/sites")
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(summary = "List sites for a client", description = "Returns only sites belonging to the requested client.")
    public PageResponse<InspectionSiteResponse> findByClient(
            @PathVariable UUID clientId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InspectionSiteStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return toPageResponse(inspectionSiteService.findByClientId(clientId, search, status, page, size, sort));
    }

    @GetMapping("/sites/{id}")
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(summary = "Get an inspection site by id", description = "Returns active or inactive site data.")
    public InspectionSiteResponse findById(@PathVariable UUID id) {
        return inspectionSiteMapper.toResponse(inspectionSiteService.findById(id));
    }

    @PostMapping("/sites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "Create an inspection site",
            description = "Creates a site linked to an existing client. The client link cannot be changed later.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Created site representation",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = InspectionSiteResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "id": "7ae3e623-419b-4adf-8da4-7f9bd7c3dd35",
                                              "clientId": "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed",
                                              "name": "Unidade Sorocaba",
                                              "addressLine": "Av. das Industrias, 1000",
                                              "city": "Sorocaba",
                                              "state": "SP",
                                              "postalCode": "18000-000",
                                              "latitude": -23.5015,
                                              "longitude": -47.4526,
                                              "status": "ACTIVE",
                                              "version": 0
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid payload",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Client not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public InspectionSiteResponse create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateInspectionSiteRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "clientId": "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed",
                                      "name": "Unidade Sorocaba",
                                      "description": "Fabrica principal",
                                      "addressLine": "Av. das Industrias, 1000",
                                      "city": "Sorocaba",
                                      "state": "SP",
                                      "postalCode": "18000-000",
                                      "latitude": -23.5015,
                                      "longitude": -47.4526,
                                      "contactName": "Joao da Silva",
                                      "contactPhone": "+55 15 99999-0000"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody CreateInspectionSiteRequest request
    ) {
        InspectionSite site = inspectionSiteService.createSite(
                request.clientId(),
                request.name(),
                request.description(),
                request.addressLine(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.latitude(),
                request.longitude(),
                request.contactName(),
                request.contactPhone(),
                request.status()
        );
        return inspectionSiteMapper.toResponse(site);
    }

    @PutMapping("/sites/{id}")
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "Update an inspection site",
            description = "Updates site data without accepting a clientId, keeping the client relationship immutable.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Site updated"),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid payload",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Site not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public InspectionSiteResponse update(
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateInspectionSiteRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "name": "Unidade Sorocaba",
                                      "addressLine": "Av. das Industrias, 1000",
                                      "city": "Sorocaba",
                                      "state": "SP",
                                      "postalCode": "18000-000",
                                      "latitude": -23.5015,
                                      "longitude": -47.4526
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody UpdateInspectionSiteRequest request
    ) {
        return inspectionSiteMapper.toResponse(inspectionSiteService.updateSite(
                id,
                request.name(),
                request.description(),
                request.addressLine(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.latitude(),
                request.longitude(),
                request.contactName(),
                request.contactPhone()
        ));
    }

    @PatchMapping("/sites/{id}/status")
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "Update an inspection site status",
            description = "Changes status without deleting the site or its historical relationships.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Site status updated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = InspectionSiteResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "id": "7ae3e623-419b-4adf-8da4-7f9bd7c3dd35",
                                              "clientId": "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed",
                                              "name": "Unidade Sorocaba",
                                              "status": "INACTIVE",
                                              "version": 1
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid payload",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Site not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public InspectionSiteResponse updateStatus(
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateInspectionSiteStatusRequest.class),
                            examples = @ExampleObject(value = "{\"status\":\"INACTIVE\"}")
                    )
            )
            @Valid @RequestBody UpdateInspectionSiteStatusRequest request
    ) {
        return inspectionSiteMapper.toResponse(inspectionSiteService.updateStatus(id, request.status()));
    }

    private PageResponse<InspectionSiteResponse> toPageResponse(
            org.springframework.data.domain.Page<InspectionSite> page
    ) {
        return PageResponse.from(page.map(inspectionSiteMapper::toResponse));
    }
}
