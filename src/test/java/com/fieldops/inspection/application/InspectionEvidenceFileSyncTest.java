package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionEvidence;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.inspection.infrastructure.storage.EvidenceStorage;
import com.fieldops.inspection.presentation.dto.EvidenceMetadataRequest;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.domain.exception.UnsupportedFileTypeException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionEvidenceFileSyncTest {

    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x10, 0x20};

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final InspectionEvidenceRepository evidenceRepository = mock(InspectionEvidenceRepository.class);
    private final EvidenceStorage storage = mock(EvidenceStorage.class);
    private final AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
    private final Authentication authentication = mock(Authentication.class);
    private final InspectionEvidenceService service = new InspectionEvidenceService(
            inspectionRepository, evidenceRepository, mock(InspectionResponseRepository.class),
            mock(NonConformityRepository.class), storage, new EvidenceFileValidator(), userResolver,
            mock(AuditEventService.class), 1024
    );

    private final UUID technicianId = UUID.randomUUID();
    private final UUID inspectionId = UUID.randomUUID();
    private final UUID evidenceId = UUID.randomUUID();
    private final AtomicReference<InspectionEvidence> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userResolver.requireUserId(authentication)).thenReturn(technicianId);
        Inspection inspection = mock(Inspection.class);
        when(inspection.getStatus()).thenReturn(InspectionStatus.IN_PROGRESS);
        when(inspectionRepository.findWithSnapshotsByIdAndTechnicianId(inspectionId, technicianId))
                .thenReturn(Optional.of(inspection));
        when(evidenceRepository.findById(evidenceId)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(evidenceRepository.saveAndFlush(any(InspectionEvidence.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void metadataFirstThenFailedUploadThenSuccessfulResend() {
        InspectionEvidence pending = service.registerFromSynchronization(
                inspectionId, metadata(), "image/jpeg", JPEG.length, sha256(JPEG)
        );
        assertThat(pending.getServerReceivedAt()).isNotNull();
        assertThat(pending.isFileReceived()).isFalse();
        verify(storage, never()).replace(anyString(), any());

        assertThatThrownBy(() -> service.uploadFromSynchronization(
                inspectionId, metadata(), new byte[]{0x01, 0x02, 0x03}, "image/jpeg"
        )).isInstanceOf(UnsupportedFileTypeException.class);
        assertThat(stored.get().isFileReceived()).isFalse();

        InspectionEvidence received = service.uploadFromSynchronization(inspectionId, metadata(), JPEG, "image/jpeg");

        assertThat(received).isSameAs(pending);
        assertThat(received.isFileReceived()).isTrue();
        assertThat(received.getChecksum()).isEqualTo(sha256(JPEG));
        verify(storage).replace("inspections/" + inspectionId + "/" + evidenceId + ".jpg", JPEG);
    }

    @Test
    void identicalResendDoesNotRewriteTheObjectNorCreateAnotherRecord() {
        service.registerFromSynchronization(inspectionId, metadata(), "image/jpeg", JPEG.length, sha256(JPEG));
        service.uploadFromSynchronization(inspectionId, metadata(), JPEG, "image/jpeg");

        service.uploadFromSynchronization(inspectionId, metadata(), JPEG, "image/jpeg");

        verify(storage, times(1)).replace(anyString(), any());
        verify(storage, never()).store(anyString(), any());
        verify(evidenceRepository, times(2)).saveAndFlush(any(InspectionEvidence.class));
    }

    @Test
    void pendingFileCannotBeDownloaded() {
        service.registerFromSynchronization(inspectionId, metadata(), "image/jpeg", JPEG.length, sha256(JPEG));
        User technician = mock(User.class);
        when(technician.getId()).thenReturn(technicianId);
        when(technician.getRole()).thenReturn(UserRole.TECHNICIAN);
        when(userResolver.requireUser(authentication)).thenReturn(technician);

        assertThatThrownBy(() -> service.download(evidenceId)).isInstanceOf(ResourceNotFoundException.class);
        verify(storage, never()).read(anyString());
    }

    private EvidenceMetadataRequest metadata() {
        return new EvidenceMetadataRequest(
                evidenceId, null, null, "PHOTO", Instant.parse("2026-11-20T10:00:00Z"), null, null, null, null
        );
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
