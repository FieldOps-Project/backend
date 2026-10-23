package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.InspectionTemplateService;
import com.fieldops.inspection.application.TemplateItemCommand;
import com.fieldops.inspection.domain.model.TemplateStatus;
import com.fieldops.inspection.presentation.dto.CreateInspectionTemplateRequest;
import com.fieldops.inspection.presentation.dto.CreateTemplateItemRequest;
import com.fieldops.inspection.presentation.dto.CreateTemplateSectionRequest;
import com.fieldops.inspection.presentation.dto.InspectionTemplateDetailResponse;
import com.fieldops.inspection.presentation.dto.InspectionTemplateResponse;
import com.fieldops.inspection.presentation.dto.ReorderTemplateItemsRequest;
import com.fieldops.inspection.presentation.dto.ReorderTemplateSectionsRequest;
import com.fieldops.inspection.presentation.dto.TemplateItemResponse;
import com.fieldops.inspection.presentation.dto.TemplateSectionResponse;
import com.fieldops.inspection.presentation.dto.UpdateInspectionTemplateRequest;
import com.fieldops.inspection.presentation.dto.UpdateTemplateItemRequest;
import com.fieldops.inspection.presentation.dto.UpdateTemplateSectionRequest;
import com.fieldops.inspection.presentation.mapper.InspectionTemplateMapper;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspection-templates")
@Tag(name = "Inspection Templates", description = "Template builder: draft editing of sections and items.")
public class InspectionTemplateController {

    private static final String DETAIL_EXAMPLE = """
            {
              "id": "5b8e1f0a-2d3c-4e5f-8a9b-0c1d2e3f4a5b",
              "title": "Checklist de extintores",
              "description": "Inspecao mensal dos extintores de incendio.",
              "category": "Seguranca contra incendio",
              "status": "DRAFT",
              "currentVersion": null,
              "draftVersionId": "9a7b6c5d-4e3f-4a1b-8c2d-1e0f9a8b7c6d",
              "sections": [{
                "id": "8f0c7f5e-4c1b-4f43-9d56-0f4f2a7c1b10",
                "title": "Condicoes fisicas",
                "description": null,
                "displayOrder": 1,
                "items": [{
                  "id": "3c2d9a41-77e0-4a8e-a1f6-5b9e0d7c2e31",
                  "sectionId": "8f0c7f5e-4c1b-4f43-9d56-0f4f2a7c1b10",
                  "code": "EXT-03",
                  "title": "A protecao do equipamento esta integra?",
                  "description": "Verifique trincas, ausencia de parafusos e partes soltas.",
                  "responseType": "CONFORMITY",
                  "required": true,
                  "observationRequiredOnFailure": true,
                  "evidenceRequiredOnFailure": true,
                  "options": [],
                  "displayOrder": 1
                }]
              }],
              "version": 0
            }
            """;

    private static final String ITEM_EXAMPLE = """
            {
              "id": "3c2d9a41-77e0-4a8e-a1f6-5b9e0d7c2e31",
              "sectionId": "8f0c7f5e-4c1b-4f43-9d56-0f4f2a7c1b10",
              "code": "EXT-03",
              "title": "A protecao do equipamento esta integra?",
              "description": "Verifique trincas, ausencia de parafusos e partes soltas.",
              "responseType": "CONFORMITY",
              "required": true,
              "observationRequiredOnFailure": true,
              "evidenceRequiredOnFailure": true,
              "options": [],
              "displayOrder": 3
            }
            """;

    private final InspectionTemplateService templateService;
    private final InspectionTemplateMapper templateMapper;

    public InspectionTemplateController(
            InspectionTemplateService templateService,
            InspectionTemplateMapper templateMapper
    ) {
        this.templateService = templateService;
        this.templateMapper = templateMapper;
    }

    // ── Template ────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "List inspection templates",
            description = "Searches templates by title or description, filtered by status and category.",
            responses = @ApiResponse(responseCode = "200", description = "Paginated template list")
    )
    public PageResponse<InspectionTemplateResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TemplateStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return PageResponse.from(templateService.findAll(search, status, category, page, size, sort)
                .map(templateMapper::toResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Get a template with its draft",
            description = "Returns template metadata and the ordered sections and items of the working draft.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Template with draft structure",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = InspectionTemplateDetailResponse.class),
                                    examples = @ExampleObject(value = DETAIL_EXAMPLE)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Template not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public InspectionTemplateDetailResponse findById(@PathVariable UUID id) {
        return templateMapper.toDetail(templateService.findDraft(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Create a template",
            description = "Creates a template in DRAFT status with an empty working draft.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Template created",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = InspectionTemplateDetailResponse.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid payload",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Title already used by another template",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public InspectionTemplateDetailResponse create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateInspectionTemplateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "Checklist de extintores",
                                      "description": "Inspecao mensal dos extintores de incendio.",
                                      "category": "Seguranca contra incendio"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody CreateInspectionTemplateRequest request
    ) {
        return templateMapper.toDetail(templateService.createTemplate(
                request.title(), request.description(), request.category()
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Update template metadata",
            description = "Updates title, description and category. Structure changes use the section and item endpoints.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Template updated"),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Template not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Title already used by another template",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public InspectionTemplateDetailResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInspectionTemplateRequest request
    ) {
        return templateMapper.toDetail(templateService.updateTemplate(
                id, request.title(), request.description(), request.category()
        ));
    }

    // ── Sections ────────────────────────────────────────────────────────

    @PostMapping("/{id}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Add a section to the draft",
            description = "Inserts the section at displayOrder, shifting the following sections, or appends it.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Section created"),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Template not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public TemplateSectionResponse createSection(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTemplateSectionRequest request
    ) {
        return templateMapper.toSection(templateService.addSection(
                id, request.title(), request.description(), request.displayOrder()
        ));
    }

    @PutMapping("/{id}/sections/order")
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Reorder draft sections",
            description = "Applies the full list of section ids as the new order in a single transaction.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Sections in the new order"),
                    @ApiResponse(
                            responseCode = "422",
                            description = "The list does not contain every draft section exactly once",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public List<TemplateSectionResponse> reorderSections(
            @PathVariable UUID id,
            @Valid @RequestBody ReorderTemplateSectionsRequest request
    ) {
        return templateMapper.toSections(templateService.reorderSections(id, request.sectionIds()));
    }

    @PutMapping("/{id}/sections/{sectionId}")
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Update a draft section",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Section updated"),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Section not found in this template",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Section belongs to a published version",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public TemplateSectionResponse updateSection(
            @PathVariable UUID id,
            @PathVariable UUID sectionId,
            @Valid @RequestBody UpdateTemplateSectionRequest request
    ) {
        return templateMapper.toSection(templateService.updateSection(
                id, sectionId, request.title(), request.description()
        ));
    }

    @DeleteMapping("/{id}/sections/{sectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Delete a draft section",
            description = "Removes the section and its items, then renumbers the remaining sections.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Section deleted"),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Section belongs to a published version",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public void deleteSection(@PathVariable UUID id, @PathVariable UUID sectionId) {
        templateService.deleteSection(id, sectionId);
    }

    // ── Items ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/sections/{sectionId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Add an item to a draft section",
            description = "Options are required for SINGLE_CHOICE only; observation and evidence on failure "
                    + "only apply to CONFORMITY and BOOLEAN.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Item created",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = TemplateItemResponse.class),
                                    examples = @ExampleObject(value = ITEM_EXAMPLE)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Missing response type or options incoherent with the type",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Section belongs to a published version",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public TemplateItemResponse createItem(
            @PathVariable UUID id,
            @PathVariable UUID sectionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateTemplateItemRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "EXT-03",
                                      "title": "A protecao do equipamento esta integra?",
                                      "description": "Verifique trincas, ausencia de parafusos e partes soltas.",
                                      "responseType": "CONFORMITY",
                                      "required": true,
                                      "observationRequiredOnFailure": true,
                                      "evidenceRequiredOnFailure": true,
                                      "displayOrder": 3
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody CreateTemplateItemRequest request
    ) {
        TemplateItemCommand command = new TemplateItemCommand(
                request.code(),
                request.title(),
                request.description(),
                request.responseType(),
                request.required(),
                request.observationRequiredOnFailure(),
                request.evidenceRequiredOnFailure(),
                request.options()
        );
        return templateMapper.toItem(templateService.addItem(id, sectionId, command, request.displayOrder()));
    }

    @PutMapping("/{id}/sections/{sectionId}/items/order")
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Reorder the items of a draft section",
            description = "Applies the full list of item ids as the new order in a single transaction.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Section with items in the new order"),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Section belongs to a published version",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "The list does not contain every item of the section exactly once",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public TemplateSectionResponse reorderItems(
            @PathVariable UUID id,
            @PathVariable UUID sectionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReorderTemplateItemsRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "itemIds": [
                                        "3c2d9a41-77e0-4a8e-a1f6-5b9e0d7c2e31",
                                        "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                                        "6f5e4d3c-2b1a-4f9e-8d7c-6b5a4f3e2d1c"
                                      ]
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody ReorderTemplateItemsRequest request
    ) {
        return templateMapper.toSection(templateService.reorderItems(id, sectionId, request.itemIds()));
    }

    @PutMapping("/{id}/items/{itemId}")
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Update a draft item",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item updated"),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Missing response type or options incoherent with the type",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Item belongs to a published version",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public TemplateItemResponse updateItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateTemplateItemRequest request
    ) {
        TemplateItemCommand command = new TemplateItemCommand(
                request.code(),
                request.title(),
                request.description(),
                request.responseType(),
                request.required(),
                request.observationRequiredOnFailure(),
                request.evidenceRequiredOnFailure(),
                request.options()
        );
        return templateMapper.toItem(templateService.updateItem(id, itemId, command));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    @Operation(
            summary = "Delete a draft item",
            description = "Removes the item and renumbers the remaining items of the section.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Item deleted"),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Item belongs to a published version",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public void deleteItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        templateService.deleteItem(id, itemId);
    }
}
