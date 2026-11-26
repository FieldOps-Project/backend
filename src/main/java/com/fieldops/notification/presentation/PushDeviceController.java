package com.fieldops.notification.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.notification.application.PushDeviceService;
import com.fieldops.notification.presentation.dto.PushDeviceResponse;
import com.fieldops.notification.presentation.dto.RegisterPushDeviceRequest;
import com.fieldops.shared.presentation.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mobile/devices")
@Tag(name = "Mobile push devices", description = "Technician-owned Expo push device registrations.")
@SecurityRequirement(name = "bearerAuth")
public class PushDeviceController {

    private final PushDeviceService service;
    private final PushDeviceMapper mapper;

    public PushDeviceController(PushDeviceService service, PushDeviceMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @Operation(
            summary = "Register a technician push device",
            description = "Registers or updates one device for the authenticated technician. The token is stored server-side and is never returned.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = RegisterPushDeviceRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "deviceId": "device-7f4a",
                                      "pushToken": "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]",
                                      "platform": "ANDROID"
                                    }
                                    """)
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Device registered",
                            content = @Content(schema = @Schema(implementation = PushDeviceResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid device registration",
                            content = @Content(schema = @Schema(implementation = ApiError.class))),
                    @ApiResponse(responseCode = "403", description = "Only technicians can register devices",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public PushDeviceResponse register(@Valid @RequestBody RegisterPushDeviceRequest request) {
        return mapper.toResponse(service.register(request));
    }

    @DeleteMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_EXECUTE)
    @Operation(
            summary = "Remove a technician push device",
            description = "Removes the device registration owned by the authenticated technician.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Device removed"),
                    @ApiResponse(responseCode = "403", description = "Only technicians can remove devices",
                            content = @Content(schema = @Schema(implementation = ApiError.class)))
            }
    )
    public void remove(@PathVariable String deviceId) {
        service.remove(deviceId);
    }
}
