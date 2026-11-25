package com.fieldops.sync.application;

import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionEvidence;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.inspection.domain.model.InspectionResponseConformity;
import com.fieldops.inspection.domain.model.InspectionResponseValue;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.NonConformity;
import com.fieldops.inspection.domain.model.NonConformitySeverity;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.sync.presentation.dto.SyncOperationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncConflictResolverTest {

    @Mock
    private InspectionRepository inspectionRepository;
    @Mock
    private InspectionResponseRepository responseRepository;
    @Mock
    private NonConformityRepository nonConformityRepository;
    @Mock
    private InspectionEvidenceRepository evidenceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SyncConflictResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SyncConflictResolver(
                inspectionRepository, responseRepository, nonConformityRepository, evidenceRepository, objectMapper
        );
    }

    @Test
    void detectsApprovedInspectionPrecedenceConflict() {
        UUID inspectionId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Inspection inspection = mock(Inspection.class);
        when(inspection.getId()).thenReturn(inspectionId);
        when(inspection.getTechnicianId()).thenReturn(technicianId);
        when(inspection.getStatus()).thenReturn(InspectionStatus.APPROVED);
        when(inspection.getVersion()).thenReturn(4);
        when(inspection.getApprovedAt()).thenReturn(Instant.parse("2026-11-24T12:00:00Z"));
        when(inspection.getUpdatedAt()).thenReturn(Instant.parse("2026-11-24T12:00:00Z"));
        when(inspectionRepository.findById(inspectionId))
                .thenReturn(Optional.of(inspection));

        Optional<SyncConflictResolver.ConflictDetails> result =
                resolver.checkInspectionPrecedence(inspectionId, technicianId);

        assertThat(result).isPresent();
        assertThat(result.get().errorCode()).isEqualTo(SyncConflictResolver.CODE_APPROVED_CONFLICT);
        assertThat(result.get().entityVersion()).isEqualTo(4);
        assertThat(result.get().serverState().get("status").asString()).isEqualTo("APPROVED");
    }

    @Test
    void detectsCanceledInspectionPrecedenceConflict() {
        UUID inspectionId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        Inspection inspection = mock(Inspection.class);
        when(inspection.getId()).thenReturn(inspectionId);
        when(inspection.getTechnicianId()).thenReturn(technicianId);
        when(inspection.getStatus()).thenReturn(InspectionStatus.CANCELED);
        when(inspection.getVersion()).thenReturn(2);
        when(inspection.getCanceledAt()).thenReturn(Instant.parse("2026-11-24T14:00:00Z"));
        when(inspection.getCanceledReason()).thenReturn("Substituida");
        when(inspectionRepository.findById(inspectionId))
                .thenReturn(Optional.of(inspection));

        Optional<SyncConflictResolver.ConflictDetails> result =
                resolver.checkInspectionPrecedence(inspectionId, technicianId);

        assertThat(result).isPresent();
        assertThat(result.get().errorCode()).isEqualTo(SyncConflictResolver.CODE_CANCELED_CONFLICT);
        assertThat(result.get().entityVersion()).isEqualTo(2);
        assertThat(result.get().serverState().get("canceledReason").asString()).isEqualTo("Substituida");
    }

    @Test
    void resolvesResponseVersionConflictWithServerStateSnapshot() {
        UUID inspectionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        InspectionResponse response = InspectionResponse.create(
                responseId, inspectionId, itemId, UUID.randomUUID(),
                new InspectionResponseValue(null, null, null, null, null, InspectionResponseConformity.CONFORMING),
                "Server value", Instant.parse("2026-11-24T10:00:00Z"), Instant.parse("2026-11-24T10:05:00Z"), 0
        );
        ReflectionTestUtils.setField(response, "version", 5);
        ReflectionTestUtils.setField(response, "updatedAt", Instant.parse("2026-11-24T10:05:00Z"));
        when(responseRepository.findById(responseId)).thenReturn(Optional.of(response));

        SyncOperationRequest operation = new SyncOperationRequest(
                UUID.randomUUID(), "INSPECTION_RESPONSE", responseId, "UPSERT", 1, List.of(),
                objectMapper.createObjectNode()
        );
        SyncConflictResolver.ConflictDetails details = resolver.resolveConflict(
                operation, inspectionId, itemId,
                new ConflictException("INSPECTION_RESPONSE_VERSION_CONFLICT", "stale")
        );

        assertThat(details.errorCode()).isEqualTo(SyncConflictResolver.CODE_VERSION_CONFLICT);
        assertThat(details.entityVersion()).isEqualTo(5);
        assertThat(details.serverState().get("conformity").asString()).isEqualTo("CONFORMING");
        assertThat(details.serverState().get("observation").asString()).isEqualTo("Server value");
    }

    @Test
    void resolvesNonConformityVersionConflictWithServerStateSnapshot() {
        UUID inspectionId = UUID.randomUUID();
        UUID ncId = UUID.randomUUID();
        NonConformity nc = NonConformity.create(
                ncId, inspectionId, null, null, "Server NC", "Updated on server",
                NonConformitySeverity.HIGH, UUID.randomUUID(),
                Instant.parse("2026-11-24T10:00:00Z"), Instant.parse("2026-11-24T10:05:00Z")
        );
        ReflectionTestUtils.setField(nc, "version", 3);
        when(nonConformityRepository.findById(ncId)).thenReturn(Optional.of(nc));

        SyncOperationRequest operation = new SyncOperationRequest(
                UUID.randomUUID(), "NON_CONFORMITY", ncId, "UPDATE", 1, List.of(),
                objectMapper.createObjectNode()
        );
        SyncConflictResolver.ConflictDetails details = resolver.resolveConflict(
                operation, inspectionId, null,
                new ConflictException("NON_CONFORMITY_VERSION_CONFLICT", "stale")
        );

        assertThat(details.errorCode()).isEqualTo(SyncConflictResolver.CODE_VERSION_CONFLICT);
        assertThat(details.entityVersion()).isEqualTo(3);
        assertThat(details.serverState().get("title").asString()).isEqualTo("Server NC");
    }

    @Test
    void resolvesEvidenceConflictWithServerStateSnapshot() {
        UUID inspectionId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        InspectionEvidence evidence = InspectionEvidence.create(
                evidenceId, inspectionId, null, null, "PHOTO", "inspections/x/y.jpg", "image/jpeg",
                1024L, "a".repeat(64), "Foto", null, null, null,
                Instant.parse("2026-11-24T10:00:00Z"), Instant.parse("2026-11-24T10:05:00Z"), null, UUID.randomUUID()
        );
        ReflectionTestUtils.setField(evidence, "version", 2);
        when(evidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));

        SyncOperationRequest operation = new SyncOperationRequest(
                UUID.randomUUID(), "INSPECTION_EVIDENCE", evidenceId, "CREATE", 0, List.of(),
                objectMapper.createObjectNode()
        );
        SyncConflictResolver.ConflictDetails details = resolver.resolveConflict(
                operation, inspectionId, null,
                new ConflictException("EVIDENCE_DELETED_CONFLICT", "deleted")
        );

        assertThat(details.errorCode()).isEqualTo("EVIDENCE_DELETED_CONFLICT");
        assertThat(details.entityVersion()).isEqualTo(2);
        assertThat(details.serverState().get("mimeType").asString()).isEqualTo("image/jpeg");
    }
}
