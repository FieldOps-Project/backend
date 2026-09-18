package com.fieldops.client.presentation.mapper;

import com.fieldops.client.domain.model.Client;
import com.fieldops.client.presentation.dto.ClientResponse;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

    public ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getLegalName(),
                client.getDocument(),
                client.getEmail(),
                client.getPhone(),
                client.getStatus(),
                client.getCreatedAt(),
                client.getUpdatedAt(),
                client.getVersion()
        );
    }
}
