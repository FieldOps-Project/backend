package com.fieldops.inspection.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.application.InspectionReviewService;
import com.fieldops.inspection.application.InspectionTransitionResult;
import com.fieldops.inspection.presentation.dto.ApproveInspectionRequest;
import com.fieldops.inspection.presentation.dto.InspectionReviewResponse;
import com.fieldops.inspection.presentation.dto.InspectionTransitionResponse;
import com.fieldops.inspection.presentation.dto.RejectInspectionRequest;
import com.fieldops.inspection.presentation.mapper.InspectionReviewMapper;
import com.fieldops.inspection.presentation.mapper.InspectionTransitionMapper;
import com.fieldops.shared.presentation.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}")
@Tag(name = "Inspection reviews", description = "Supervisor review cycles: begin, approve and reject.")
@SecurityRequirement(name = "bearerAuth")
public class InspectionReviewController {

    private final InspectionReviewService reviewService;
    private final InspectionReviewMapper reviewMapper;
    private final InspectionTransitionMapper transitionMapper;

    public InspectionReviewController(
            InspectionReviewService reviewService,
            InspectionReviewMapper reviewMapper,
            InspectionTransitionMapper transitionMapper
    ) {
        this.reviewService = reviewService;
        this.reviewMapper = reviewMapper;
        this.transitionMapper = transitionMapper;
    }

    @GetMapping("/reviews")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_REVIEW)
    @Operation(
            summary = "List the review cycles of an inspection",
            description = "Returns every decision ordered by review cycle; earlier rounds are never overwritten.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Review cycles returned"),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public List<InspectionReviewResponse> findAll(@PathVariable UUID inspectionId) {
        return reviewService.findAll(inspectionId).stream()
                .map(reviewMapper::toResponse)
                .toList();
    }

    @PostMapping("/begin-review")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_REVIEW)
    @Operation(
            summary = "Begin the review of a submitted inspection",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inspection moved to UNDER_REVIEW",
                            content = @Content(schema = @Schema(implementation = InspectionTransitionResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "409", description = "Inspection is not SUBMITTED",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionTransitionResponse beginReview(@PathVariable UUID inspectionId) {
        InspectionTransitionResult result = reviewService.beginReview(inspectionId);
        return transitionMapper.toResponse(result.inspection(), result.previousStatus());
    }

    @PostMapping("/approve")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_REVIEW)
    @Operation(
            summary = "Approve an inspection under review",
            description = "Records reviewer, date and optional comment as a new review cycle and locks the inspection.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inspection approved",
                            content = @Content(schema = @Schema(implementation = InspectionReviewResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "id": "0c4f9a3e-8f0e-4b8b-9f43-77f3a1d2c001",
                                              "inspectionId": "3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5",
                                              "reviewCycle": 2,
                                              "reviewerId": "7a1e5b52-2c1f-4f7e-9b9b-1f2a3c4d5e6f",
                                              "decision": "APPROVED",
                                              "reason": null,
                                              "comments": "Inspecao conferida sem pendencias.",
                                              "itemsToCorrect": [],
                                              "reviewedAt": "2026-08-02T14:10:00Z",
                                              "createdAt": "2026-08-02T14:10:00Z"
                                            }
                                            """))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "409", description = "Inspection is not UNDER_REVIEW",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionReviewResponse approve(
            @PathVariable UUID inspectionId,
            @Valid @RequestBody(required = false) ApproveInspectionRequest request
    ) {
        return reviewMapper.toResponse(reviewService.approve(inspectionId, request == null ? null : request.comments()));
    }

    @PostMapping("/reject")
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_REVIEW)
    @Operation(
            summary = "Reject an inspection under review",
            description = "Records the mandatory reason and the snapshot items to correct as a new review cycle.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inspection rejected",
                            content = @Content(schema = @Schema(implementation = InspectionReviewResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "id": "5b2d7c11-3e4f-4a5b-8c6d-7e8f9a0b1c02",
                                              "inspectionId": "3d8dc050-54f4-4d3b-9e5d-5fdb29ae3ed5",
                                              "reviewCycle": 1,
                                              "reviewerId": "7a1e5b52-2c1f-4f7e-9b9b-1f2a3c4d5e6f",
                                              "decision": "REJECTED",
                                              "reason": "A fotografia do item 4 nao permite identificar o numero de serie.",
                                              "comments": null,
                                              "itemsToCorrect": ["ab18a638-1c45-4a15-a444-b4235506e912"],
                                              "reviewedAt": "2026-08-01T18:30:00Z",
                                              "createdAt": "2026-08-01T18:30:00Z"
                                            }
                                            """))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "404", description = "Inspection not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "409", description = "Inspection is not UNDER_REVIEW",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "422", description = "Missing reason or item outside the inspection snapshot",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public InspectionReviewResponse reject(
            @PathVariable UUID inspectionId,
            @Valid @RequestBody RejectInspectionRequest request
    ) {
        return reviewMapper.toResponse(reviewService.reject(
                inspectionId, request.reason(), request.comments(), request.itemsToCorrect()
        ));
    }
}
