package com.fieldops.equipment.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentJpaMappingTest {

    @Test
    void equipmentCreationAndFieldsMapping() {
        UUID siteId = UUID.randomUUID();
        LocalDate now = LocalDate.now();

        Equipment equipment = Equipment.create(
                siteId,
                "Gerador Principal",
                "AST-100",
                "SN-555",
                "Caterpillar",
                "C15",
                "Gerador diesel 500 kVA",
                "QR-GER-001",
                EquipmentStatus.ACTIVE,
                now
        );

        assertThat(equipment.getSiteId()).isEqualTo(siteId);
        assertThat(equipment.getName()).isEqualTo("Gerador Principal");
        assertThat(equipment.getAssetNumber()).isEqualTo("AST-100");
        assertThat(equipment.getSerialNumber()).isEqualTo("SN-555");
        assertThat(equipment.getManufacturer()).isEqualTo("Caterpillar");
        assertThat(equipment.getModel()).isEqualTo("C15");
        assertThat(equipment.getDescription()).isEqualTo("Gerador diesel 500 kVA");
        assertThat(equipment.getQrCode()).isEqualTo("QR-GER-001");
        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.ACTIVE);
        assertThat(equipment.getInstalledAt()).isEqualTo(now);
    }
}
