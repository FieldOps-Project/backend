package com.fieldops.inspection.application;

import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionItemSnapshotRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionProgressProjection;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionListServiceTest {

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final InspectionItemSnapshotRepository snapshotRepository = mock(InspectionItemSnapshotRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final InspectionSiteRepository siteRepository = mock(InspectionSiteRepository.class);
    private final EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
    private final InspectionListService service = new InspectionListService(
            inspectionRepository,
            snapshotRepository,
            clientRepository,
            siteRepository,
            equipmentRepository,
            userRepository,
            userResolver
    );

    private final UUID supervisorId = UUID.randomUUID();
    private final UUID inspectionId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final UUID equipmentId = UUID.randomUUID();
    private final UUID technicianId = UUID.randomUUID();
    private final Authentication authentication = mock(Authentication.class);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User supervisor = mock(User.class);
        when(supervisor.getId()).thenReturn(supervisorId);
        when(supervisor.getRole()).thenReturn(UserRole.SUPERVISOR);
        when(userResolver.requireUser(authentication)).thenReturn(supervisor);

        Inspection inspection = mock(Inspection.class);
        when(inspection.getId()).thenReturn(inspectionId);
        when(inspection.getTitle()).thenReturn("Extintor planta sul");
        when(inspection.getClientId()).thenReturn(clientId);
        when(inspection.getSiteId()).thenReturn(siteId);
        when(inspection.getEquipmentId()).thenReturn(equipmentId);
        when(inspection.getTechnicianId()).thenReturn(technicianId);
        when(inspection.getPriority()).thenReturn(InspectionPriority.HIGH);
        when(inspection.getStatus()).thenReturn(InspectionStatus.IN_PROGRESS);
        when(inspection.getScheduledFor()).thenReturn(Instant.now().minusSeconds(60));
        when(inspection.getUpdatedAt()).thenReturn(Instant.now());
        when(inspectionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inspection)));

        InspectionProgressProjection progress = mock(InspectionProgressProjection.class);
        when(progress.getInspectionId()).thenReturn(inspectionId);
        when(progress.getApplicableItems()).thenReturn(5L);
        when(snapshotRepository.countApplicableItems(any())).thenReturn(List.of(progress));

        Client client = mock(Client.class);
        when(client.getId()).thenReturn(clientId);
        when(client.getName()).thenReturn("Cliente Sul");
        when(clientRepository.findAllById(any())).thenReturn(List.of(client));

        InspectionSite site = mock(InspectionSite.class);
        when(site.getId()).thenReturn(siteId);
        when(site.getName()).thenReturn("Planta Sul");
        when(siteRepository.findAllById(any())).thenReturn(List.of(site));

        Equipment equipment = mock(Equipment.class);
        when(equipment.getId()).thenReturn(equipmentId);
        when(equipment.getName()).thenReturn("Extintor 01");
        when(equipmentRepository.findAllById(any())).thenReturn(List.of(equipment));

        User technician = mock(User.class);
        when(technician.getId()).thenReturn(technicianId);
        when(technician.getName()).thenReturn("Maria Silva");
        when(userRepository.findAllById(any())).thenReturn(List.of(technician));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findAllClampsPageSizeAndBuildsDerivedAdministrativeFields() {
        Page<InspectionListItem> result = service.findAll(new InspectionListQuery(
                "extintor",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                500,
                null
        ));

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.clientName()).isEqualTo("Cliente Sul");
            assertThat(item.siteName()).isEqualTo("Planta Sul");
            assertThat(item.equipmentName()).isEqualTo("Extintor 01");
            assertThat(item.technicianName()).isEqualTo("Maria Silva");
            assertThat(item.applicableItems()).isEqualTo(5);
            assertThat(item.answeredItems()).isZero();
            assertThat(item.overdue()).isTrue();
        });

        var pageableCaptor = forClass(Pageable.class);
        verify(inspectionRepository).findAll(any(Specification.class), pageableCaptor.capture());
        verify(snapshotRepository).countApplicableItems(any());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("scheduledFor").isAscending()).isTrue();
    }

    @Test
    void rejectsUnknownSortFieldBeforeQueryingRepository() {
        assertThatThrownBy(() -> service.findAll(new InspectionListQuery(
                null, null, null, null, null, null, null, null, null, null,
                false, 0, 20, "password,asc"
        )))
                .isInstanceOf(com.fieldops.shared.domain.exception.InvalidRequestException.class)
                .hasMessageContaining("allowed inspection field");

        verify(inspectionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }
}
