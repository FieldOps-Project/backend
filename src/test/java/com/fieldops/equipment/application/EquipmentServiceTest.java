package com.fieldops.equipment.application;

import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.domain.model.Equipment;
import com.fieldops.equipment.domain.model.EquipmentStatus;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.equipment.presentation.dto.CreateEquipmentRequest;
import com.fieldops.equipment.presentation.dto.UpdateEquipmentRequest;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private InspectionSiteRepository inspectionSiteRepository;

    @InjectMocks
    private EquipmentService equipmentService;

    private UUID siteId;
    private InspectionSite activeSite;
    private InspectionSite inactiveSite;

    @BeforeEach
    void setUp() {
        siteId = UUID.randomUUID();
        Client client = Client.create("Client A", "Legal A", "12345678000190", "a@client.com", "11999990000", ClientStatus.ACTIVE);
        activeSite = InspectionSite.create(client, "Site A", "Desc", "Addr", "City", "SP", "14000-000", null, null, "Contact", "Phone", InspectionSiteStatus.ACTIVE);
        inactiveSite = InspectionSite.create(client, "Site B", "Desc", "Addr", "City", "SP", "14000-000", null, null, "Contact", "Phone", InspectionSiteStatus.INACTIVE);
    }

    @Test
    void createEquipmentSuccess() {
        CreateEquipmentRequest request = new CreateEquipmentRequest(
                siteId, "Compressor", "AST-01", "SN-01", "Schulz", "M1", "Desc", "QR-001", EquipmentStatus.ACTIVE, LocalDate.now()
        );

        when(inspectionSiteRepository.findById(siteId)).thenReturn(Optional.of(activeSite));
        when(equipmentRepository.existsByQrCode("QR-001")).thenReturn(false);
        when(equipmentRepository.saveAndFlush(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Equipment created = equipmentService.createEquipment(request);

        assertThat(created.getName()).isEqualTo("Compressor");
        assertThat(created.getQrCode()).isEqualTo("QR-001");
        verify(equipmentRepository).saveAndFlush(any(Equipment.class));
    }

    @Test
    void createEquipmentThrowsWhenSiteNotFound() {
        CreateEquipmentRequest request = new CreateEquipmentRequest(
                siteId, "Compressor", null, null, null, null, null, "QR-001", EquipmentStatus.ACTIVE, null
        );

        when(inspectionSiteRepository.findById(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> equipmentService.createEquipment(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Inspection site with id " + siteId + " not found");
    }

    @Test
    void createEquipmentThrowsWhenSiteIsInactive() {
        CreateEquipmentRequest request = new CreateEquipmentRequest(
                siteId, "Compressor", null, null, null, null, null, "QR-001", EquipmentStatus.ACTIVE, null
        );

        when(inspectionSiteRepository.findById(siteId)).thenReturn(Optional.of(inactiveSite));

        assertThatThrownBy(() -> equipmentService.createEquipment(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot create equipment for an inactive inspection site");
    }

    @Test
    void createEquipmentThrowsWhenQrCodeExists() {
        CreateEquipmentRequest request = new CreateEquipmentRequest(
                siteId, "Compressor", null, null, null, null, null, "QR-DUP", EquipmentStatus.ACTIVE, null
        );

        when(inspectionSiteRepository.findById(siteId)).thenReturn(Optional.of(activeSite));
        when(equipmentRepository.existsByQrCode("QR-DUP")).thenReturn(true);

        assertThatThrownBy(() -> equipmentService.createEquipment(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Equipment with QR code 'QR-DUP' already exists");
    }

    @Test
    void getEquipmentByQrCodeSuccess() {
        Equipment equipment = Equipment.create(siteId, "Motor", null, null, null, null, null, "QR-MOTOR", EquipmentStatus.ACTIVE, null);
        when(equipmentRepository.findByQrCode("QR-MOTOR")).thenReturn(Optional.of(equipment));

        Equipment found = equipmentService.getEquipmentByQrCode("QR-MOTOR");

        assertThat(found.getName()).isEqualTo("Motor");
        assertThat(found.getQrCode()).isEqualTo("QR-MOTOR");
    }

    @Test
    void updateEquipmentThrowsWhenQrCodeDuplicate() {
        UUID equipmentId = UUID.randomUUID();
        Equipment existing = Equipment.create(siteId, "Bomba", null, null, null, null, null, "QR-OLD", EquipmentStatus.ACTIVE, null);
        UpdateEquipmentRequest request = new UpdateEquipmentRequest("Bomba Nova", null, null, null, null, null, "QR-TAKEN", null);

        when(equipmentRepository.findById(equipmentId)).thenReturn(Optional.of(existing));
        when(equipmentRepository.existsByQrCodeAndIdNot("QR-TAKEN", equipmentId)).thenReturn(true);

        assertThatThrownBy(() -> equipmentService.updateEquipment(equipmentId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Equipment with QR code 'QR-TAKEN' already exists");
    }
}
