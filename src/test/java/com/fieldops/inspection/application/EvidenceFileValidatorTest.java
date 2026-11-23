package com.fieldops.inspection.application;

import com.fieldops.shared.domain.exception.PayloadTooLargeException;
import com.fieldops.shared.domain.exception.UnsupportedFileTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceFileValidatorTest {

    private final EvidenceFileValidator validator = new EvidenceFileValidator();

    @Test
    void acceptsJpegWhenDeclaredTypeMatchesItsSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "anything.bin", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01}
        );

        EvidenceFileValidator.ValidatedEvidenceFile result = validator.validate(file, 100);

        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo("jpg");
    }

    @Test
    void rejectsFakeImageContentAndUnsupportedTypes() {
        MockMultipartFile fakeImage = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "not an image".getBytes()
        );
        MockMultipartFile text = new MockMultipartFile(
                "file", "photo.txt", "text/plain", "text".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(fakeImage, 100))
                .isInstanceOf(UnsupportedFileTypeException.class);
        assertThatThrownBy(() -> validator.validate(text, 100))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void rejectsFilesAboveTheConfiguredLimit() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01}
        );

        assertThatThrownBy(() -> validator.validate(file, 3))
                .isInstanceOf(PayloadTooLargeException.class);
    }
}
