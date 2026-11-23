package com.fieldops.inspection.presentation.mapper;

import com.fieldops.inspection.application.InspectionMobileAnswer;
import com.fieldops.inspection.application.InspectionMobileChecklistItem;
import com.fieldops.inspection.application.InspectionMobileDetail;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.presentation.dto.MobileInspectionResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionMobileMapperTest {

    @Test
    void mapsSnapshotJsonAsStructuredPayload() {
        UUID itemId = UUID.randomUUID();
        InspectionMobileDetail detail = new InspectionMobileDetail(
                UUID.randomUUID(),
                "Gerador",
                InspectionStatus.IN_PROGRESS,
                InspectionPriority.HIGH,
                Instant.parse("2026-11-23T10:00:00Z"),
                Instant.parse("2026-11-23T11:00:00Z"),
                "Desligar antes da inspecao",
                3,
                UUID.randomUUID(),
                "Industria ABC",
                UUID.randomUUID(),
                "Fabrica",
                "Rua A",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new InspectionMobileChecklistItem(
                        itemId, "Seguranca", null, 1, "GEN-01", "Lacre intacto", null,
                        ResponseType.CONFORMITY, true,
                        "{\"evidenceRequiredOnFailure\":true}", "[\"Sim\",\"Nao\"]", 1
                )),
                List.of(new InspectionMobileAnswer(itemId, "true", "Tudo certo", List.of())),
                null
        );

        MobileInspectionResponse response = new InspectionMobileMapper().toResponse(detail);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.rules().get("evidenceRequiredOnFailure").asBoolean()).isTrue();
            assertThat(item.options()).hasSize(2);
            assertThat(item.options().get(0).asText()).isEqualTo("Sim");
            assertThat(item.options().get(1).asText()).isEqualTo("Nao");
        });
        assertThat(response.responses()).singleElement().satisfies(answer -> {
            assertThat(answer.value().asBoolean()).isTrue();
            assertThat(answer.observation()).isEqualTo("Tudo certo");
        });
    }
}
