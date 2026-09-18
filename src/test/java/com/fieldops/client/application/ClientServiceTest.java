package com.fieldops.client.application;

import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository);
    }

    @Test
    void createClient_normalizesOptionalFieldsAndDefaultsToActive() {
        when(clientRepository.saveAndFlush(any(Client.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Client created = clientService.createClient(
                " Industria Alfa ",
                " Industria Alfa Ltda ",
                "12.345.678/0001-90",
                " CONTATO@INDUSTRIA-ALFA.COM.BR ",
                " +55 11 99999-0000 ",
                null
        );

        assertThat(created.getName()).isEqualTo("Industria Alfa");
        assertThat(created.getLegalName()).isEqualTo("Industria Alfa Ltda");
        assertThat(created.getDocument()).isEqualTo("12345678000190");
        assertThat(created.getEmail()).isEqualTo("contato@industria-alfa.com.br");
        assertThat(created.getPhone()).isEqualTo("+55 11 99999-0000");
        assertThat(created.getStatus()).isEqualTo(ClientStatus.ACTIVE);
    }

    @Test
    void createClient_rejectsInvalidDocumentFormat() {
        assertThatThrownBy(() -> clientService.createClient(
                "Industria Alfa", null, "invalid", null, null, null
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Document must use a CPF or CNPJ format");

        verify(clientRepository, never()).saveAndFlush(any(Client.class));
    }

    @Test
    void findAll_buildsBoundedPageableAndReturnsFilteredPage() {
        Client client = Client.create("Industria Alfa", null, null, null, null, ClientStatus.ACTIVE);
        when(clientRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(client), invocation.getArgument(1), 101));

        var result = clientService.findAll(" alfa ", ClientStatus.ACTIVE, 0, 500, "name,asc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(clientRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("name").isAscending()).isTrue();
        assertThat(result.getContent()).containsExactly(client);
        assertThat(result.getTotalElements()).isEqualTo(101);
    }

    @Test
    void updateClient_changesProfileWithoutChangingStatus() {
        UUID clientId = UUID.randomUUID();
        Client client = Client.create("Old name", null, null, null, null, ClientStatus.INACTIVE);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.saveAndFlush(any(Client.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Client updated = clientService.updateClient(
                clientId,
                "New name",
                "New legal name",
                "12345678901",
                "hello@example.com",
                null
        );

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getLegalName()).isEqualTo("New legal name");
        assertThat(updated.getDocument()).isEqualTo("12345678901");
        assertThat(updated.getEmail()).isEqualTo("hello@example.com");
        assertThat(updated.getStatus()).isEqualTo(ClientStatus.INACTIVE);
    }

    @Test
    void updateStatus_inactivatesWithoutPhysicalDelete() {
        UUID clientId = UUID.randomUUID();
        Client client = Client.create("Industria Alfa", null, null, null, null, ClientStatus.ACTIVE);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Client updated = clientService.updateStatus(clientId, ClientStatus.INACTIVE);

        assertThat(updated.getStatus()).isEqualTo(ClientStatus.INACTIVE);
        verify(clientRepository).save(client);
        verify(clientRepository, never()).delete(any(Client.class));
        verify(clientRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void findForScheduling_rejectsInactiveClient() {
        UUID clientId = UUID.randomUUID();
        Client client = Client.create("Industria Alfa", null, null, null, null, ClientStatus.INACTIVE);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> clientService.findForScheduling(clientId))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "CLIENT_INACTIVE");
    }

    @Test
    void findById_throwsNotFoundWhenClientDoesNotExist() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.findById(clientId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(clientId.toString());
    }
}
