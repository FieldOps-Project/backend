package com.fieldops.client.application;

import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
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

import java.math.BigDecimal;
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
class InspectionSiteServiceTest {

    @Mock
    private InspectionSiteRepository inspectionSiteRepository;

    @Mock
    private ClientRepository clientRepository;

    private InspectionSiteService inspectionSiteService;
    private Client client;

    @BeforeEach
    void setUp() {
        inspectionSiteService = new InspectionSiteService(inspectionSiteRepository, clientRepository);
        client = Client.create("Industria Alfa", null, null, null, null, ClientStatus.ACTIVE);
    }

    @Test
    void createSite_requiresExistingClientAndDefaultsToActive() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(inspectionSiteRepository.saveAndFlush(any(InspectionSite.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InspectionSite created = inspectionSiteService.createSite(
                clientId,
                " Unidade Sorocaba ",
                " Fabrica principal ",
                " Av. das Industrias, 1000 ",
                " Sorocaba ",
                " SP ",
                " 18000-000 ",
                BigDecimal.valueOf(-23.5015),
                BigDecimal.valueOf(-47.4526),
                " Joao da Silva ",
                " +55 15 99999-0000 ",
                null
        );

        assertThat(created.getClient()).isSameAs(client);
        assertThat(created.getName()).isEqualTo("Unidade Sorocaba");
        assertThat(created.getDescription()).isEqualTo("Fabrica principal");
        assertThat(created.getAddressLine()).isEqualTo("Av. das Industrias, 1000");
        assertThat(created.getCity()).isEqualTo("Sorocaba");
        assertThat(created.getState()).isEqualTo("SP");
        assertThat(created.getPostalCode()).isEqualTo("18000-000");
        assertThat(created.getStatus()).isEqualTo(InspectionSiteStatus.ACTIVE);
    }

    @Test
    void createSite_returnsNotFoundWhenClientDoesNotExist() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inspectionSiteService.createSite(
                clientId, "Unidade Sorocaba", null, null, null, null, null,
                null, null, null, null, null
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(clientId.toString());

        verify(inspectionSiteRepository, never()).saveAndFlush(any(InspectionSite.class));
    }

    @Test
    void findAll_limitsPageSizeAndSupportsClientFilter() {
        InspectionSite site = InspectionSite.create(
                client, "Unidade Sorocaba", null, null, "Sorocaba", "SP", null,
                null, null, null, null, InspectionSiteStatus.ACTIVE
        );
        when(inspectionSiteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(site), invocation.getArgument(1), 101));

        var result = inspectionSiteService.findAll(
                UUID.randomUUID(), " sorocaba ", InspectionSiteStatus.ACTIVE, 0, 500, "city,asc"
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(inspectionSiteRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("city").isAscending()).isTrue();
        assertThat(result.getContent()).containsExactly(site);
        assertThat(result.getTotalElements()).isEqualTo(101);
    }

    @Test
    void findByClientId_validatesClientAndQueriesThatClient() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(inspectionSiteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = inspectionSiteService.findByClientId(
                clientId, null, InspectionSiteStatus.ACTIVE, 0, 20, null
        );

        assertThat(result).isEmpty();
        verify(clientRepository).findById(clientId);
        verify(inspectionSiteRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void updateSite_keepsOriginalClientRelationship() {
        UUID siteId = UUID.randomUUID();
        InspectionSite site = InspectionSite.create(
                client, "Old name", null, null, null, null, null,
                null, null, null, null, InspectionSiteStatus.INACTIVE
        );
        when(inspectionSiteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(inspectionSiteRepository.saveAndFlush(any(InspectionSite.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InspectionSite updated = inspectionSiteService.updateSite(
                siteId, "New name", "Description", null, "Sorocaba", "SP", null,
                null, null, null, null
        );

        assertThat(updated.getClient()).isSameAs(client);
        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getStatus()).isEqualTo(InspectionSiteStatus.INACTIVE);
    }

    @Test
    void updateStatus_inactivatesWithoutPhysicalDelete() {
        UUID siteId = UUID.randomUUID();
        InspectionSite site = InspectionSite.create(
                client, "Unidade Sorocaba", null, null, null, null, null,
                null, null, null, null, InspectionSiteStatus.ACTIVE
        );
        when(inspectionSiteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(inspectionSiteRepository.save(any(InspectionSite.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InspectionSite updated = inspectionSiteService.updateStatus(siteId, InspectionSiteStatus.INACTIVE);

        assertThat(updated.getStatus()).isEqualTo(InspectionSiteStatus.INACTIVE);
        verify(inspectionSiteRepository).save(site);
        verify(inspectionSiteRepository, never()).delete(any(InspectionSite.class));
        verify(inspectionSiteRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void findForScheduling_rejectsInactiveSite() {
        UUID siteId = UUID.randomUUID();
        InspectionSite site = InspectionSite.create(
                client, "Unidade Sorocaba", null, null, null, null, null,
                null, null, null, null, InspectionSiteStatus.INACTIVE
        );
        when(inspectionSiteRepository.findById(siteId)).thenReturn(Optional.of(site));

        assertThatThrownBy(() -> inspectionSiteService.findForScheduling(siteId))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_SITE_INACTIVE");
    }

    @Test
    void findForScheduling_rejectsWhenClientIsInactive() {
        UUID siteId = UUID.randomUUID();
        Client inactiveClient = Client.create("Industria Alfa", null, null, null, null, ClientStatus.INACTIVE);
        InspectionSite site = InspectionSite.create(
                inactiveClient, "Unidade Sorocaba", null, null, null, null, null,
                null, null, null, null, InspectionSiteStatus.ACTIVE
        );
        when(inspectionSiteRepository.findById(siteId)).thenReturn(Optional.of(site));

        assertThatThrownBy(() -> inspectionSiteService.findForScheduling(siteId))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "CLIENT_INACTIVE");
    }

    @Test
    void createSite_rejectsIncompleteOrOutOfRangeCoordinates() {
        UUID clientId = UUID.randomUUID();

        assertThatThrownBy(() -> inspectionSiteService.createSite(
                clientId, "Unidade Sorocaba", null, null, null, null, null,
                BigDecimal.ONE, null, null, null, null
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_COORDINATES");

        assertThatThrownBy(() -> inspectionSiteService.createSite(
                clientId, "Unidade Sorocaba", null, null, null, null, null,
                BigDecimal.valueOf(91), BigDecimal.ONE, null, null, null
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Latitude must be between -90 and 90");

        verify(clientRepository, never()).findById(clientId);
    }
}
