package com.fieldops.sync.application;

import tools.jackson.databind.ObjectMapper;
import com.fieldops.inspection.application.InspectionEvidenceService;
import com.fieldops.inspection.application.InspectionResponseService;
import com.fieldops.inspection.application.NonConformityService;
import com.fieldops.inspection.domain.model.InspectionEvidence;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.infrastructure.filter.RequestIdFilter;
import com.fieldops.sync.domain.model.SyncOperationResultStatus;
import com.fieldops.sync.domain.model.SyncProcessedOperation;
import com.fieldops.sync.infrastructure.persistence.SyncProcessedOperationRepository;
import com.fieldops.sync.presentation.dto.SyncOperationRequest;
import com.fieldops.sync.presentation.dto.SyncOperationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncOperationProcessorTest {

    @Mock
    private SyncProcessedOperationRepository processedOperationRepository;
    @Mock
    private InspectionResponseService responseService;
    @Mock
    private InspectionEvidenceService evidenceService;
    @Mock
    private NonConformityService nonConformityService;
    @Mock
    private SyncConflictResolver conflictResolver;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private SyncOperationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SyncOperationProcessor(
                processedOperationRepository, responseService, evidenceService, nonConformityService,
                conflictResolver, new ObjectMapper(), jdbcTemplate
        );
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void returnsAlreadyAppliedFromThePersistentRecordWithoutReexecutingTheOperation() {
        UUID operationId = UUID.randomUUID();
        SyncProcessedOperation existing = SyncProcessedOperation.record(
                operationId, UUID.randomUUID(), UUID.randomUUID(), "INSPECTION_RESPONSE", UUID.randomUUID(),
                SyncOperationResultStatus.APPLIED, 3, null, null, Instant.now()
        );
        when(processedOperationRepository.findById(operationId)).thenReturn(Optional.of(existing));

        SyncOperationResult result = processor.process(
                existing.getDeviceId(), existing.getUserId(), operation(operationId, "UNKNOWN")
        );

        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.ALREADY_APPLIED);
        assertThat(result.entityVersion()).isEqualTo(3);
        verify(processedOperationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(responseService, evidenceService, nonConformityService);
    }

    @Test
    void storesAnIndividualRejectedResultForAnUnsupportedOperation() {
        UUID operationId = UUID.randomUUID();
        when(processedOperationRepository.findById(operationId)).thenReturn(Optional.empty());
        when(processedOperationRepository.saveAndFlush(any(SyncProcessedOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SyncOperationResult result = processor.process(
                UUID.randomUUID(), UUID.randomUUID(), operation(operationId, "UNKNOWN")
        );

        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.REJECTED);
        assertThat(result.errorCode()).isEqualTo("SYNC_ENTITY_TYPE_UNSUPPORTED");
        ArgumentCaptor<SyncProcessedOperation> captor = ArgumentCaptor.forClass(SyncProcessedOperation.class);
        verify(processedOperationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getResultStatus()).isEqualTo(SyncOperationResultStatus.REJECTED);
    }

    @Test
    void appliesAResponsePayloadAndPersistsItsVersion() {
        UUID operationId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        InspectionResponse response = org.mockito.Mockito.mock(InspectionResponse.class);
        when(response.getVersion()).thenReturn(1);
        when(processedOperationRepository.findById(operationId)).thenReturn(Optional.empty());
        when(processedOperationRepository.saveAndFlush(any(SyncProcessedOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(responseService.applyFromSynchronization(eq(inspectionId), any()))
                .thenReturn(response);

        var payload = new ObjectMapper().createObjectNode()
                .put("inspectionId", inspectionId.toString())
                .put("inspectionItemId", itemId.toString())
                .put("conformity", "NON_CONFORMING")
                .put("observation", "Trinca visivel");
        SyncOperationRequest operation = new SyncOperationRequest(
                operationId, "INSPECTION_RESPONSE", UUID.randomUUID(), "UPSERT", 0, List.of(), payload
        );

        SyncOperationResult result = processor.process(UUID.randomUUID(), UUID.randomUUID(), operation);

        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.APPLIED);
        assertThat(result.entityVersion()).isEqualTo(1);
        verify(responseService).applyFromSynchronization(eq(inspectionId), any());
    }

    @Test
    void appliesEvidenceMetadataWithoutWaitingForTheFile() {
        UUID operationId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        String checksum = "a".repeat(64);
        InspectionEvidence evidence = org.mockito.Mockito.mock(InspectionEvidence.class);
        when(evidence.getVersion()).thenReturn(0);
        when(processedOperationRepository.findById(operationId)).thenReturn(Optional.empty());
        when(processedOperationRepository.saveAndFlush(any(SyncProcessedOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceService.registerFromSynchronization(eq(inspectionId), any(), eq("image/jpeg"), eq(2048L),
                eq(checksum), eq(0))).thenReturn(evidence);

        var payload = new ObjectMapper().createObjectNode()
                .put("inspectionId", inspectionId.toString())
                .put("type", "PHOTO")
                .put("mimeType", "image/jpeg")
                .put("sizeBytes", 2048)
                .put("checksum", checksum);
        SyncOperationResult result = processor.process(UUID.randomUUID(), UUID.randomUUID(), new SyncOperationRequest(
                operationId, "INSPECTION_EVIDENCE", UUID.randomUUID(), "CREATE", 0, List.of(), payload
        ));

        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.APPLIED);
        verify(evidenceService, never()).uploadFromSynchronization(any(), any(), any(), any());
    }

    @Test
    void rejectsEvidenceMetadataWithoutChecksum() {
        UUID operationId = UUID.randomUUID();
        when(processedOperationRepository.findById(operationId)).thenReturn(Optional.empty());
        when(processedOperationRepository.saveAndFlush(any(SyncProcessedOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var payload = new ObjectMapper().createObjectNode()
                .put("inspectionId", UUID.randomUUID().toString())
                .put("mimeType", "image/jpeg")
                .put("sizeBytes", 2048);
        SyncOperationResult result = processor.process(UUID.randomUUID(), UUID.randomUUID(), new SyncOperationRequest(
                operationId, "INSPECTION_EVIDENCE", UUID.randomUUID(), "CREATE", 0, List.of(), payload
        ));

        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.REJECTED);
        assertThat(result.errorCode()).isEqualTo("EVIDENCE_FILE_DESCRIPTION_REQUIRED");
        verifyNoInteractions(evidenceService);
    }

    @Test
    void returnsConflictWithServerStateWhenResponseVersionIsStale() {
        UUID operationId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MDC.put(RequestIdFilter.MDC_KEY, "req-conflict-1");

        var serverState = new ObjectMapper().createObjectNode()
                .put("conformity", "CONFORMING")
                .put("observation", "Server value");
        when(processedOperationRepository.findById(operationId)).thenReturn(Optional.empty());
        when(processedOperationRepository.saveAndFlush(any(SyncProcessedOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conflictResolver.checkInspectionPrecedence(inspectionId, userId)).thenReturn(Optional.empty());
        when(responseService.applyFromSynchronization(eq(inspectionId), any()))
                .thenThrow(new ConflictException("INSPECTION_RESPONSE_VERSION_CONFLICT", "stale"));
        when(conflictResolver.resolveConflict(any(), eq(inspectionId), eq(itemId), any()))
                .thenReturn(new SyncConflictResolver.ConflictDetails(
                        5, SyncConflictResolver.CODE_VERSION_CONFLICT,
                        "The record was modified on the server after the last synchronization", serverState
                ));

        var payload = new ObjectMapper().createObjectNode()
                .put("inspectionId", inspectionId.toString())
                .put("inspectionItemId", itemId.toString())
                .put("conformity", "NON_CONFORMING");
        SyncOperationResult result = processor.process(deviceId, userId, new SyncOperationRequest(
                operationId, "INSPECTION_RESPONSE", responseId, "UPSERT", 2, List.of(), payload
        ));

        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.CONFLICT);
        assertThat(result.entityVersion()).isEqualTo(5);
        assertThat(result.errorCode()).isEqualTo(SyncConflictResolver.CODE_VERSION_CONFLICT);
        assertThat(result.serverState().get("observation").asString()).isEqualTo("Server value");
    }

    @Test
    void rejectsApprovedInspectionOperationAsExplicitConflictBeforeApplying() {
        UUID operationId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var serverState = new ObjectMapper().createObjectNode().put("status", "APPROVED");
        when(processedOperationRepository.findById(operationId)).thenReturn(Optional.empty());
        when(processedOperationRepository.saveAndFlush(any(SyncProcessedOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conflictResolver.checkInspectionPrecedence(inspectionId, userId))
                .thenReturn(Optional.of(new SyncConflictResolver.ConflictDetails(
                        3, SyncConflictResolver.CODE_APPROVED_CONFLICT,
                        "Approved inspection data on the server takes precedence and cannot be updated",
                        serverState
                )));

        var payload = new ObjectMapper().createObjectNode()
                .put("inspectionId", inspectionId.toString())
                .put("inspectionItemId", UUID.randomUUID().toString());
        SyncOperationResult result = processor.process(UUID.randomUUID(), userId, new SyncOperationRequest(
                operationId, "INSPECTION_RESPONSE", UUID.randomUUID(), "UPSERT", 1, List.of(), payload
        ));

        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.CONFLICT);
        assertThat(result.errorCode()).isEqualTo(SyncConflictResolver.CODE_APPROVED_CONFLICT);
        assertThat(result.entityVersion()).isEqualTo(3);
        assertThat(result.serverState().get("status").asString()).isEqualTo("APPROVED");
        verifyNoInteractions(responseService);
    }

    @Test
    void preservesConflictStatusAndServerStateWhenRetryingConflictedOperation() {
        UUID operationId = UUID.randomUUID();
        SyncProcessedOperation existing = SyncProcessedOperation.record(
                operationId, UUID.randomUUID(), UUID.randomUUID(), "INSPECTION_RESPONSE", UUID.randomUUID(),
                SyncOperationResultStatus.CONFLICT, 5, SyncConflictResolver.CODE_VERSION_CONFLICT,
                "Modified on server", "{\"observation\":\"Server value\"}", Instant.now()
        );
        when(processedOperationRepository.findById(operationId)).thenReturn(Optional.of(existing));

        SyncOperationResult result = processor.process(
                existing.getDeviceId(), existing.getUserId(), operation(operationId, "INSPECTION_RESPONSE")
        );

        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.CONFLICT);
        assertThat(result.entityVersion()).isEqualTo(5);
        assertThat(result.errorCode()).isEqualTo(SyncConflictResolver.CODE_VERSION_CONFLICT);
        assertThat(result.serverState().get("observation").asString()).isEqualTo("Server value");
        verify(processedOperationRepository, never()).saveAndFlush(any());
    }

    @Test
    void passesBaseVersionWhenUpdatingNonConformity() {
        UUID operationId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        UUID ncId = UUID.randomUUID();
        var nc = org.mockito.Mockito.mock(com.fieldops.inspection.domain.model.NonConformity.class);
        when(nc.getVersion()).thenReturn(2);
        when(processedOperationRepository.findById(operationId)).thenReturn(Optional.empty());
        when(processedOperationRepository.saveAndFlush(any(SyncProcessedOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(nonConformityService.updateFromSynchronization(eq(ncId), any(), eq(1))).thenReturn(nc);

        var payload = new ObjectMapper().createObjectNode()
                .put("inspectionId", inspectionId.toString())
                .put("title", "Updated NC")
                .put("description", "Details")
                .put("severity", "HIGH");
        SyncOperationResult result = processor.process(UUID.randomUUID(), UUID.randomUUID(), new SyncOperationRequest(
                operationId, "NON_CONFORMITY", ncId, "UPDATE", 1, List.of(), payload
        ));

        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.APPLIED);
        assertThat(result.entityVersion()).isEqualTo(2);
        verify(nonConformityService).updateFromSynchronization(eq(ncId), any(), eq(1));
    }

    private SyncOperationRequest operation(UUID id, String entityType) {
        return new SyncOperationRequest(
                id, entityType, UUID.randomUUID(), "UPSERT", 0, null,
                new ObjectMapper().createObjectNode()
        );
    }
}
