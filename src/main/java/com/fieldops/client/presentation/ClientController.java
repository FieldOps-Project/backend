package com.fieldops.client.presentation;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.client.application.ClientService;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
import com.fieldops.client.presentation.dto.ClientResponse;
import com.fieldops.client.presentation.dto.CreateClientRequest;
import com.fieldops.client.presentation.dto.UpdateClientRequest;
import com.fieldops.client.presentation.dto.UpdateClientStatusRequest;
import com.fieldops.client.presentation.mapper.ClientMapper;
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
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients", description = "Client organization management endpoints.")
public class ClientController {

    private final ClientService clientService;
    private final ClientMapper clientMapper;

    public ClientController(ClientService clientService, ClientMapper clientMapper) {
        this.clientService = clientService;
        this.clientMapper = clientMapper;
    }

    @GetMapping
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "List clients",
            description = "Searches clients by name or legal name with bounded pagination.",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Paginated client list",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [{
                                        "id": "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed",
                                        "name": "Industria Alfa",
                                        "legalName": "Industria Alfa Ltda",
                                        "document": "12345678000190",
                                        "email": "contato@industria-alfa.com.br",
                                        "phone": "+55 11 99999-0000",
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
    public PageResponse<ClientResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ClientStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return PageResponse.from(clientService.findAll(search, status, page, size, sort)
                .map(clientMapper::toResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(summary = "Get a client by id", description = "Returns active or inactive client data.")
    public ClientResponse findById(@PathVariable UUID id) {
        return clientMapper.toResponse(clientService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "Create a client",
            description = "Creates an active client by default. The document is stored normalized without punctuation.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Created client representation",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ClientResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "id": "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed",
                                              "name": "Industria Alfa",
                                              "legalName": "Industria Alfa Ltda",
                                              "document": "12345678000190",
                                              "email": "contato@industria-alfa.com.br",
                                              "phone": "+55 11 99999-0000",
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
                            responseCode = "403",
                            description = "Insufficient permissions",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public ClientResponse create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateClientRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "name": "Industria Alfa",
                                      "legalName": "Industria Alfa Ltda",
                                      "document": "12.345.678/0001-90",
                                      "email": "contato@industria-alfa.com.br",
                                      "phone": "+55 11 99999-0000"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody CreateClientRequest request
    ) {
        Client client = clientService.createClient(
                request.name(),
                request.legalName(),
                request.document(),
                request.email(),
                request.phone(),
                request.status()
        );
        return clientMapper.toResponse(client);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "Update a client",
            description = "Updates profile data. Status is changed separately by the status endpoint.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Client updated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ClientResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "id": "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed",
                                              "name": "Industria Alfa",
                                              "legalName": "Industria Alfa Ltda",
                                              "document": "12345678000190",
                                              "status": "ACTIVE",
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
                            description = "Client not found",
                            content = @Content(schema = @Schema(implementation = ApiError.class))
                    )
            }
    )
    public ClientResponse update(
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateClientRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "name": "Industria Alfa",
                                      "legalName": "Industria Alfa Ltda",
                                      "document": "12345678000190",
                                      "email": "contato@industria-alfa.com.br",
                                      "phone": "+55 11 99999-0000"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody UpdateClientRequest request
    ) {
        return clientMapper.toResponse(clientService.updateClient(
                id,
                request.name(),
                request.legalName(),
                request.document(),
                request.email(),
                request.phone()
        ));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    @Operation(
            summary = "Update a client status",
            description = "Changes status without deleting the client or its historical relationships.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Client status updated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ClientResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "id": "2fe4585d-6d89-4b1f-9125-b6a63fc8c2ed",
                                              "name": "Industria Alfa",
                                              "status": "INACTIVE",
                                              "version": 2
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
    public ClientResponse updateStatus(
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateClientStatusRequest.class),
                            examples = @ExampleObject(value = "{\"status\":\"INACTIVE\"}")
                    )
            )
            @Valid @RequestBody UpdateClientStatusRequest request
    ) {
        return clientMapper.toResponse(clientService.updateStatus(id, request.status()));
    }
}
