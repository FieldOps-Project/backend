package com.fieldops.sync.application;

import tools.jackson.databind.ObjectMapper;
import com.fieldops.inspection.application.InspectionEvidenceService;
import com.fieldops.inspection.application.InspectionResponseService;
import com.fieldops.inspection.application.NonConformityService;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.sync.domain.model.SyncOperationResultStatus;
import com.fieldops.sync.domain.model.SyncProcessedOperation;
import com.fieldops.sync.infrastructure.persistence.SyncProcessedOperationRepository;
import com.fieldops.sync.presentation.dto.SyncOperationRequest;
import com.fieldops.sync.presentation.dto.SyncOperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private JdbcTemplate jdbcTemplate;

    private SyncOperationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SyncOperationProcessor(
                processedOperationRepository, responseService, evidenceService, nonConformityService,
                new ObjectMapper(), jdbcTemplate
        );
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

    private SyncOperationRequest operation(UUID id, String entityType) {
        return new SyncOperationRequest(
                id, entityType, UUID.randomUUID(), "UPSERT", 0, null,
                new ObjectMapper().createObjectNode()
        );
    }
}
