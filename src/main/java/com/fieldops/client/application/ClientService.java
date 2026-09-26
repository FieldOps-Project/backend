package com.fieldops.client.application;

import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.ClientSpecifications;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.presentation.dto.PaginationDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ClientService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "legalName", "status", "createdAt", "updatedAt"
    );
    private static final Pattern DOCUMENT_PATTERN = Pattern.compile(
            "(?:[0-9]{11}|[0-9]{14}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}|[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}/[0-9]{4}-[0-9]{2})"
    );

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public Client createClient(
            String name,
            String legalName,
            String document,
            String email,
            String phone,
            ClientStatus status
    ) {
        return clientRepository.saveAndFlush(Client.create(
                name,
                normalizeOptional(legalName),
                normalizeDocument(document),
                normalizeEmail(email),
                normalizeOptional(phone),
                status
        ));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public Page<Client> findAll(
            String search,
            ClientStatus status,
            int page,
            int size,
            String sort
    ) {
        Pageable pageable = PaginationDefaults.of(page, size, parseSort(sort));
        return clientRepository.findAll(ClientSpecifications.withFilters(search, status), pageable);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public Client findById(UUID id) {
        return findRequired(id);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public Client updateClient(
            UUID id,
            String name,
            String legalName,
            String document,
            String email,
            String phone
    ) {
        Client client = findRequired(id);
        client.updateProfile(
                name,
                normalizeOptional(legalName),
                normalizeDocument(document),
                normalizeEmail(email),
                normalizeOptional(phone)
        );
        return clientRepository.saveAndFlush(client);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.CATALOG_MANAGE)
    public Client updateStatus(UUID id, ClientStatus status) {
        Client client = findRequired(id);
        client.updateStatus(status);
        return clientRepository.save(client);
    }

    /**
     * Shared scheduling guard for the future inspection scheduling use case.
     * Inactive clients remain queryable but cannot be selected for new work.
     */
    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public Client findForScheduling(UUID id) {
        Client client = findRequired(id);
        client.ensureAvailableForScheduling();
        return client;
    }

    private Client findRequired(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", id));
    }

    private String normalizeDocument(String document) {
        if (document == null || document.isBlank()) {
            return null;
        }
        String trimmed = document.trim();
        if (!DOCUMENT_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidRequestException(
                    "INVALID_CLIENT_DOCUMENT",
                    "Document must use a CPF or CNPJ format"
            );
        }
        return trimmed.replaceAll("[.\\-/]", "");
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Sort parseSort(String sort) {
        String requestedSort = sort == null || sort.isBlank() ? "createdAt,desc" : sort.trim();
        String[] parts = requestedSort.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank() || !ALLOWED_SORT_FIELDS.contains(parts[0].trim())) {
            throw invalidSort();
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException ex) {
                throw invalidSort();
            }
        }
        return Sort.by(direction, parts[0].trim());
    }

    private InvalidRequestException invalidSort() {
        return new InvalidRequestException(
                "INVALID_SORT",
                "Sort must use an allowed client field and direction asc or desc"
        );
    }
}
