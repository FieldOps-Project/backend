package com.fieldops.inspection.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.user.domain.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionMobileServiceTest {

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final InspectionSiteRepository siteRepository = mock(InspectionSiteRepository.class);
    private final EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
    private final AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
    private final InspectionMobileService service = new InspectionMobileService(
            inspectionRepository, clientRepository, siteRepository, equipmentRepository, userResolver
    );

    private final UUID technicianId = UUID.randomUUID();
    private final UUID inspectionId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final Authentication authentication = mock(Authentication.class);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        User technician = mock(User.class);
        when(technician.getId()).thenReturn(technicianId);
        when(userResolver.requireUser(authentication)).thenReturn(technician);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listUsesAuthenticatedTechnicianAndIncrementalFilters() {
        Inspection inspection = mock(Inspection.class);
        when(inspection.getId()).thenReturn(inspectionId);
        when(inspection.getTitle()).thenReturn("Gerador");
        when(inspection.getStatus()).thenReturn(InspectionStatus.ASSIGNED);
        when(inspection.getPriority()).thenReturn(InspectionPriority.HIGH);
        when(inspection.getScheduledFor()).thenReturn(Instant.parse("2026-11-23T10:00:00Z"));
        when(inspection.getUpdatedAt()).thenReturn(Instant.parse("2026-11-23T11:00:00Z"));
        when(inspection.getVersion()).thenReturn(3);
        when(inspection.getClientId()).thenReturn(clientId);
        when(inspection.getSiteId()).thenReturn(siteId);
        when(inspection.getEquipmentId()).thenReturn(null);
        when(inspectionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(inspection));

        Client client = mock(Client.class);
        when(client.getId()).thenReturn(clientId);
        when(client.getName()).thenReturn("Industria ABC");
        InspectionSite site = mock(InspectionSite.class);
        when(site.getId()).thenReturn(siteId);
        when(site.getName()).thenReturn("Fabrica");
        when(clientRepository.findAllById(any())).thenReturn(List.of(client));
        when(siteRepository.findAllById(any())).thenReturn(List.of(site));
        when(equipmentRepository.findAllById(any())).thenReturn(List.of());

        List<InspectionMobileSummary> result = service.findAll(new InspectionMobileQuery(
                InspectionStatus.ASSIGNED, Instant.parse("2026-11-22T00:00:00Z")
        ));

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(inspectionId);
            assertThat(summary.clientName()).isEqualTo("Industria ABC");
            assertThat(summary.siteName()).isEqualTo("Fabrica");
            assertThat(summary.version()).isEqualTo(3);
        });
        verify(inspectionRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void detailReturnsNotFoundWhenInspectionBelongsToAnotherTechnician() {
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(inspectionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Inspection");
    }

    @Test
    void detailPreservesSnapshotOrderAndRejectedReviewContract() {
        Inspection inspection = mock(Inspection.class);
        when(inspection.getId()).thenReturn(inspectionId);
        when(inspection.getTitle()).thenReturn("Gerador");
        when(inspection.getStatus()).thenReturn(InspectionStatus.REJECTED);
        when(inspection.getPriority()).thenReturn(InspectionPriority.HIGH);
        when(inspection.getScheduledFor()).thenReturn(Instant.now());
        when(inspection.getUpdatedAt()).thenReturn(Instant.now());
        when(inspection.getInstructions()).thenReturn("Desligar antes da inspeção");
        when(inspection.getVersion()).thenReturn(2);
        when(inspection.getClientId()).thenReturn(clientId);
        when(inspection.getSiteId()).thenReturn(siteId);
        when(inspection.getEquipmentId()).thenReturn(null);
        List<InspectionItemSnapshot> snapshots = List.of(
                snapshot(UUID.randomUUID(), 2, 1, "Segundo"),
                snapshot(UUID.randomUUID(), 1, 1, "Primeiro")
        );
        when(inspection.getItemSnapshots()).thenReturn(snapshots);
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));

        Client client = mock(Client.class);
        when(client.getId()).thenReturn(clientId);
        when(client.getName()).thenReturn("Industria ABC");
        InspectionSite site = mock(InspectionSite.class);
        when(site.getId()).thenReturn(siteId);
        when(site.getName()).thenReturn("Fabrica");
        when(site.getAddressLine()).thenReturn("Rua A");
        when(clientRepository.findAllById(any())).thenReturn(List.of(client));
        when(siteRepository.findAllById(any())).thenReturn(List.of(site));
        when(equipmentRepository.findAllById(any())).thenReturn(List.of());

        InspectionMobileDetail result = service.findById(inspectionId);

        assertThat(result.items()).extracting(InspectionMobileChecklistItem::itemTitle)
                .containsExactly("Primeiro", "Segundo");
        assertThat(result.review().decision()).isEqualTo(InspectionStatus.REJECTED);
        assertThat(result.responses()).isEmpty();
    }

    private InspectionItemSnapshot snapshot(UUID id, int sectionOrder, int itemOrder, String title) {
        InspectionItemSnapshot snapshot = mock(InspectionItemSnapshot.class);
        when(snapshot.getId()).thenReturn(id);
        when(snapshot.getSectionTitle()).thenReturn("Secao");
        when(snapshot.getSectionOrder()).thenReturn(sectionOrder);
        when(snapshot.getItemOrder()).thenReturn(itemOrder);
        when(snapshot.getItemTitle()).thenReturn(title);
        when(snapshot.getResponseType()).thenReturn(ResponseType.CONFORMITY);
        when(snapshot.getRulesJson()).thenReturn("{\"evidenceRequiredOnFailure\":true}");
        return snapshot;
    }
}
