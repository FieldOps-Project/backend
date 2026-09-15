package com.fieldops.inspection.presentation;

import com.fieldops.inspection.presentation.dto.InspectionSummaryResponse;
import com.fieldops.shared.presentation.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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

    private List<InspectionSummaryResponse> mockInspectionList() {
        return List.of(new InspectionSummaryResponse(
                UUID.fromString("3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5"),
                "Inspecao de Extintor - Planta Sul",
                "IN_PROGRESS",
                OffsetDateTime.parse("2026-09-14T10:00:00Z")
        ));
    }
}
