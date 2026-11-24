package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.InspectionHistoryService;
import com.fieldops.inspection.presentation.dto.InspectionHistoryEntryResponse;
import com.fieldops.inspection.presentation.mapper.InspectionHistoryMapper;
import com.fieldops.shared.presentation.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections/{id}")
@Tag(name = "Inspection history", description = "Read-only audit timeline of an inspection.")
@SecurityRequirement(name = "bearerAuth")
public class InspectionHistoryController {

    private final InspectionHistoryService historyService;
    private final InspectionHistoryMapper historyMapper;

    public InspectionHistoryController(InspectionHistoryService historyService, InspectionHistoryMapper historyMapper) {
        this.historyService = historyService;
        this.historyMapper = historyMapper;
    }

    @GetMapping("/history")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_HISTORY_READ)
    @Operation(
            summary = "Inspection audit timeline",
            description = "Events ordered by server time, with author, action, server time and device time when the "
                    + "event came from the field. Technicians only see their own inspections.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Timeline returned",
                            content = @Content(examples = @ExampleObject(value = """
                                    [
                                      {
                                        "id": "e1d3c1a2-6f4b-4c55-9a1e-2b3c4d5e6f70",
                                        "action": "INSPECTION_STARTED",
                                        "entityType": "INSPECTION",
                                        "entityId": "3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5",
                                        "actorId": "22222222-2222-2222-2222-222222222222",
                                        "actorName": "Tecnico Joao",
                                        "occurredAt": "2026-08-01T16:02:04Z",
                                        "deviceOccurredAt": "2026-08-01T13:02:00Z",
                                        "requestId": "6efca79e-6382-4537-accc-7c4fd57a66d5",
                                        "deviceId": null,
                                        "previousValue": "status=ASSIGNED;...",
                                        "newValue": "status=IN_PROGRESS;..."
                                      }
                                    ]
                                    """))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not found or outside the user scope",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public List<InspectionHistoryEntryResponse> history(@PathVariable UUID id) {
        return historyService.findByInspectionId(id).stream()
                .map(historyMapper::toResponse)
                .toList();
    }
}
