package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.TemplateVersionService;
import com.fieldops.inspection.presentation.dto.InspectionTemplateResponse;
import com.fieldops.inspection.presentation.dto.TemplateVersionDetailResponse;
import com.fieldops.inspection.presentation.dto.TemplateVersionSummaryResponse;
import com.fieldops.inspection.presentation.dto.UpdateTemplateStatusRequest;
import com.fieldops.inspection.presentation.dto.UpdateTemplateVersionAvailabilityRequest;
import com.fieldops.inspection.presentation.mapper.InspectionTemplateMapper;
import com.fieldops.shared.presentation.dto.ApiError;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inspection Template Versions", description = "Publication and lifecycle of immutable template versions.")
public class InspectionTemplateVersionController {

    private static final String PUBLISH_INVALID_EXAMPLE = """
            {
              "timestamp": "2026-10-23T14:00:00Z",
              "status": 422,
              "code": "TEMPLATE_PUBLISH_INVALID",
              "message": "The draft has 2 pending issue(s) that block publication",
              "path": "/api/v1/inspection-templates/5b8e1f0a-2d3c-4e5f-8a9b-0c1d2e3f4a5b/publish",
              "requestId": "c0a8012e-7f3d-4b9a-a2e1-5d6c7b8a9f01",
              "fieldErrors": [
                {"field": "sections[1].items", "message": "Section \\"Sinalizacao\\" must have at least one item"},
                {"field": "sections[0].items[2].options", "message": "SINGLE_CHOICE items require at least two non-blank options"}
              ]
            }
            """;

    private final TemplateVersionService versionService;
    private final InspectionTemplateMapper templateMapper;

    public InspectionTemplateVersionController(
            TemplateVersionService versionService,
            InspectionTemplateMapper templateMapper
    ) {
        this.versionService = versionService;
        this.templateMapper = templateMapper;
    }

    @PostMapping("/inspection-templates/{id}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Publish the draft as a new version",
            description = "Validates the draft and freezes a copy of it as the next numbered version. "
                    + "The draft stays editable. The first publication makes the template ACTIVE; "
                    + "a retired (INACTIVE) template keeps its status.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Version published"),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Template not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Draft has pending issues, listed in fieldErrors",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ApiError.class),
                                    examples = @ExampleObject(value = PUBLISH_INVALID_EXAMPLE)
                            )
                    )
            }
    )
    public TemplateVersionSummaryResponse publish(@PathVariable UUID id) {
        return templateMapper.toVersionSummary(versionService.publish(id));
    }

    @GetMapping("/inspection-templates/{id}/versions")
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "List published versions",
            description = "Returns the published versions of a template, newest first. The draft is not listed.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Published versions"),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Template not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public List<TemplateVersionSummaryResponse> findVersions(@PathVariable UUID id) {
        return versionService.findVersions(id).stream()
                .map(templateMapper::toVersionSummary)
                .toList();
    }

    @PatchMapping("/inspection-templates/{id}/status")
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Activate or retire a template",
            description = "Only ACTIVE and INACTIVE are accepted, and only after the first publication. "
                    + "Inactive templates cannot be used for new inspections; existing ones are unaffected.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Status updated"),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Status not allowed",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Template not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Template was never published",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public InspectionTemplateResponse updateTemplateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTemplateStatusRequest request
    ) {
        return templateMapper.toResponse(versionService.updateTemplateStatus(id, request.status()));
    }

    @GetMapping("/inspection-template-versions/{versionId}")
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Get a published version",
            description = "Returns the frozen sections and items of a published version.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Published version"),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Published version not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public TemplateVersionDetailResponse findVersion(@PathVariable UUID versionId) {
        return templateMapper.toVersionDetail(versionService.findVersion(versionId));
    }

    @PatchMapping("/inspection-template-versions/{versionId}/availability")
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Retire or restore a version for new inspections",
            description = "Changes only whether new inspections can use this version; its content never changes.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Availability updated"),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Published version not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public TemplateVersionSummaryResponse updateAvailability(
            @PathVariable UUID versionId,
            @Valid @RequestBody UpdateTemplateVersionAvailabilityRequest request
    ) {
        return templateMapper.toVersionSummary(
                versionService.updateAvailability(versionId, request.activeForNewInspections())
        );
    }
}
