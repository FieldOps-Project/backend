package com.fieldops.sync.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.sync.application.SyncPullService;
import com.fieldops.sync.application.SyncPushService;
import com.fieldops.sync.presentation.dto.SyncPullResponse;
import com.fieldops.sync.presentation.dto.SyncPushRequest;
import com.fieldops.sync.presentation.dto.SyncPushResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mobile/sync")
@Tag(name = "Mobile synchronization", description = "Offline operation synchronization for technicians.")
@SecurityRequirement(name = "bearerAuth")
public class SyncController {

    private final SyncPushService syncPushService;
    private final SyncPullService syncPullService;

    public SyncController(SyncPushService syncPushService, SyncPullService syncPullService) {
        this.syncPushService = syncPushService;
        this.syncPullService = syncPullService;
    }

    @GetMapping("/pull")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @Operation(
            summary = "Pull incremental mobile changes",
            description = "Returns only changes belonging to the authenticated technician. The cursor is opaque and must be stored and sent back unchanged.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Changes returned",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    schema = @Schema(implementation = SyncPullResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "changes": [{
                                                "entityType": "INSPECTION",
                                                "entityId": "62327a9a-ec19-46a2-89ae-b49086b657f9",
                                                "changeType": "UPSERT",
                                                "version": 4,
                                                "payload": {"status": "ASSIGNED"}
                                              }],
                                              "nextCursor": "eyJ0cyI6IjIwMjYtMDgtMDFUMTQ6MTU6MDBaIiwiaWQiOiJ1dWlkIn0",
                                              "hasMore": false,
                                              "serverTime": "2026-08-01T14:15:00Z"
                                            }
                                            """))),
                    @ApiResponse(responseCode = "400", description = "Invalid cursor or limit"),
                    @ApiResponse(responseCode = "403", description = "Access denied")
            }
    )
    public SyncPullResponse pull(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "200") Integer limit
    ) {
        return syncPullService.pull(cursor, limit);
    }

    @PostMapping("/push")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @Operation(
            summary = "Push offline operations",
            description = "Processes mobile operations in dependency order. Each operation is isolated and identified persistently for safe retries. "
                    + "Version mismatches or terminal server states (APPROVED/CANCELED) return CONFLICT with current serverVersion and serverState without discarding local device data.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @Schema(implementation = SyncPushRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "deviceId": "b38f54fd-6d7c-4249-b80f-5d0362103f82",
                                      "lastPullCursor": "cursor-anterior",
                                      "operations": [{
                                        "operationId": "5f30d6de-a4e0-4da8-b1c1-2acbd6f2850e",
                                        "entityType": "INSPECTION_RESPONSE",
                                        "entityId": "62327a9a-ec19-46a2-89ae-b49086b657f9",
                                        "operationType": "UPSERT",
                                        "baseVersion": 0,
                                        "dependsOn": [],
                                        "payload": {
                                          "inspectionId": "8c2b1f0f-ec16-44da-a36c-8c7d65d52f41",
                                          "inspectionItemId": "ab18a638-2c65-44c4-83a4-1bdf03b8e6d3",
                                          "conformity": "NON_CONFORMING",
                                          "observation": "Protecao lateral apresenta trinca."
                                        }
                                      }]
                                    }
                                    """))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Operations processed",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    schema = @Schema(implementation = SyncPushResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "results": [{
                                                "operationId": "5f30d6de-a4e0-4da8-b1c1-2acbd6f2850e",
                                                "status": "APPLIED",
                                                "entityVersion": 0,
                                                "errorCode": null,
                                                "errorMessage": null
                                              }],
                                              "changes": [],
                                              "nextCursor": "novo-cursor",
                                              "serverTime": "2026-08-01T14:15:00Z"
                                            }
                                            """))),
                    @ApiResponse(responseCode = "400", description = "Invalid synchronization envelope"),
                    @ApiResponse(responseCode = "413", description = "Synchronization batch exceeds the configured limit"),
                    @ApiResponse(responseCode = "403", description = "Access denied")
            }
    )
    public SyncPushResponse push(@Valid @RequestBody SyncPushRequest request) {
        return syncPushService.push(request);
    }
}
