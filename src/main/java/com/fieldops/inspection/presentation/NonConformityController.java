package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.NonConformityService;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.domain.model.NonConformityStatus;
import com.fieldops.inspection.presentation.dto.CreateNonConformityRequest;
import com.fieldops.inspection.presentation.dto.NonConformityResponse;
import com.fieldops.inspection.presentation.dto.UpdateNonConformityRequest;
import com.fieldops.inspection.presentation.dto.UpdateNonConformityStatusRequest;
import com.fieldops.inspection.presentation.mapper.NonConformityMapper;
import com.fieldops.shared.presentation.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
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
@Tag(name = "Non-conformities", description = "Inspection non-conformity records and evidence rules.")
@SecurityRequirement(name = "bearerAuth")
public class NonConformityController {

    private final ObjectProvider<NonConformityService> serviceProvider;
    private final NonConformityMapper mapper;

    public NonConformityController(
            ObjectProvider<NonConformityService> serviceProvider,
            NonConformityMapper mapper
    ) {
        this.serviceProvider = serviceProvider;
        this.mapper = mapper;
    }

    @GetMapping("/non-conformities")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_NON_CONFORMITY_READ)
    @Operation(summary = "List inspection non-conformities")
    public PageResponse<NonConformityResponse> findAll(
            @RequestParam(required = false) UUID inspectionId,
            @RequestParam(required = false) NonConformitySeverity severity,
            @RequestParam(required = false) NonConformityStatus status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service().findAll(inspectionId, severity, status, clientId, page, size)
                .map(mapper::toResponse));
    }

    @GetMapping("/non-conformities/{id}")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_NON_CONFORMITY_READ)
    @Operation(summary = "Get an inspection non-conformity")
    public NonConformityResponse findById(@PathVariable UUID id) {
        return mapper.toResponse(service().findById(id));
    }

    @PostMapping("/inspections/{inspectionId}/non-conformities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @Operation(summary = "Create an inspection non-conformity")
    public NonConformityResponse create(
            @PathVariable UUID inspectionId,
            @Valid @RequestBody CreateNonConformityRequest request
    ) {
        return mapper.toResponse(service().create(inspectionId, request));
    }

    @PutMapping("/non-conformities/{id}")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @Operation(summary = "Update an inspection non-conformity")
    public NonConformityResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNonConformityRequest request
    ) {
        return mapper.toResponse(service().update(id, request));
    }

    @PatchMapping("/non-conformities/{id}/status")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @Operation(summary = "Update an inspection non-conformity status")
    public NonConformityResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNonConformityStatusRequest request
    ) {
        return mapper.toResponse(service().changeStatus(id, request));
    }

    private NonConformityService service() {
        NonConformityService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Non-conformity persistence is not available");
        }
        return service;
    }
}
