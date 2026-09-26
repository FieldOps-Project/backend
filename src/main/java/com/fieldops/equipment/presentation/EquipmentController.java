package com.fieldops.equipment.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.equipment.application.EquipmentService;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.domain.model.EquipmentStatus;
import com.fieldops.equipment.presentation.dto.CreateEquipmentRequest;
import com.fieldops.equipment.presentation.dto.EquipmentResponse;
import com.fieldops.equipment.presentation.dto.UpdateEquipmentRequest;
import com.fieldops.equipment.presentation.dto.UpdateEquipmentStatusRequest;
import com.fieldops.equipment.presentation.mapper.EquipmentMapper;
import com.fieldops.shared.presentation.dto.ApiError;
import com.fieldops.shared.presentation.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
@Tag(name = "Equipment", description = "Equipment management and QR Code lookup endpoints.")
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final EquipmentMapper equipmentMapper;

    public EquipmentController(
            EquipmentService equipmentService,
            EquipmentMapper equipmentMapper
    ) {
        this.equipmentService = equipmentService;
        this.equipmentMapper = equipmentMapper;
    }

    @GetMapping("/equipment")
    @PreAuthorize(AuthorizationPolicies.AUTHENTICATED)
    @Operation(
            summary = "List equipment",
            description = "Lists equipment globally or filtered by site, search text, or status, with bounded pagination.",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Paginated equipment list",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [{
                                        "id": "8a50e30d-0000-4000-8000-000000000001",
                                        "siteId": "7ae3e623-419b-4adf-8da4-7f9bd7c3dd35",
                                        "name": "Compressor de Ar Industrial",
                                        "assetNumber": "AST-2026-001",
                                        "qrCode": "EQP-SITE-001-COMP-01",
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
    public PageResponse<EquipmentResponse> findAll(
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return toPageResponse(equipmentService.listEquipment(siteId, status, search, page, size, sort));
    }

    @GetMapping("/sites/{siteId}/equipment")
    @PreAuthorize(AuthorizationPolicies.AUTHENTICATED)
    @Operation(
            summary = "List equipment for a site",
            description = "Returns equipment linked to the specified site ID."
    )
    public PageResponse<EquipmentResponse> findBySite(
            @PathVariable UUID siteId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return toPageResponse(equipmentService.listEquipmentBySite(siteId, status, search, page, size, sort));
    }

    @GetMapping("/equipment/{id}")
    @PreAuthorize(AuthorizationPolicies.AUTHENTICATED)
    @Operation(summary = "Get equipment by ID", description = "Returns single equipment details.")
    public EquipmentResponse findById(@PathVariable UUID id) {
        return equipmentMapper.toResponse(equipmentService.getEquipmentById(id));
    }

    @GetMapping("/equipment/by-qr/{qrCode}")
    @PreAuthorize(AuthorizationPolicies.AUTHENTICATED)
    @Operation(summary = "Lookup equipment by QR Code", description = "Returns equipment matching the exact QR Code payload.")
    public EquipmentResponse findByQrCode(@PathVariable String qrCode) {
        return equipmentMapper.toResponse(equipmentService.getEquipmentByQrCode(qrCode));
    }

    @PostMapping("/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "Create equipment",
            description = "Creates new equipment associated with an active inspection site and a unique QR Code.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Equipment created"),
                    @ApiResponse(responseCode = "400", description = "Validation or site status error",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "409", description = "QR Code already in use",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public EquipmentResponse create(@Valid @RequestBody CreateEquipmentRequest request) {
        return equipmentMapper.toResponse(equipmentService.createEquipment(request));
    }

    @PutMapping("/equipment/{id}")
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "Update equipment",
            description = "Updates equipment attributes without changing site association.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Equipment updated"),
                    @ApiResponse(responseCode = "404", description = "Equipment not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "409", description = "QR Code already in use by another equipment",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public EquipmentResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEquipmentRequest request
    ) {
        return equipmentMapper.toResponse(equipmentService.updateEquipment(id, request));
    }

    @PatchMapping("/equipment/{id}/status")
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "Update equipment status",
            description = "Updates status to ACTIVE, INACTIVE or DECOMMISSIONED without physical deletion."
    )
    public EquipmentResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEquipmentStatusRequest request
    ) {
        return equipmentMapper.toResponse(equipmentService.updateStatus(id, request.status()));
    }

    private PageResponse<EquipmentResponse> toPageResponse(Page<Equipment> page) {
        return PageResponse.from(page.map(equipmentMapper::toResponse));
    }
}
