package com.fieldops.inspection.application;

import com.fieldops.shared.domain.exception.PayloadTooLargeException;
import com.fieldops.shared.domain.exception.UnsupportedFileTypeException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class EvidenceFileValidator {

    public ValidatedEvidenceFile validate(MultipartFile file, long maxSizeBytes) {
        if (file == null || file.isEmpty()) {
            throw unsupported("Evidence file must not be empty");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new PayloadTooLargeException("EVIDENCE_TOO_LARGE", "The evidence exceeds the configured size limit");
        }
        String contentType = file.getContentType();
        if (!"image/jpeg".equalsIgnoreCase(contentType) && !"image/png".equalsIgnoreCase(contentType)) {
            throw unsupported("Only JPEG and PNG evidence files are supported");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read evidence upload", exception);
        }
        return validate(content, contentType, maxSizeBytes);
    }

    public ValidatedEvidenceFile validate(byte[] content, String contentType, long maxSizeBytes) {
        if (content == null || content.length == 0) {
            throw unsupported("Evidence file must not be empty");
        }
        if (content.length > maxSizeBytes) {
            throw new PayloadTooLargeException("EVIDENCE_TOO_LARGE", "The evidence exceeds the configured size limit");
        }
        if (!"image/jpeg".equalsIgnoreCase(contentType) && !"image/png".equalsIgnoreCase(contentType)) {
            throw unsupported("Only JPEG and PNG evidence files are supported");
        }
        if (content.length > maxSizeBytes || !matchesSignature(content, contentType)) {
            throw unsupported("The evidence content does not match its declared image format");
        }
        return new ValidatedEvidenceFile(content, contentType.toLowerCase(), extension(contentType));
    }

    /** Checks the declared format of a file that will be sent later and returns its storage extension. */
    public String requireSupportedType(String contentType) {
        if (!"image/jpeg".equalsIgnoreCase(contentType) && !"image/png".equalsIgnoreCase(contentType)) {
            throw unsupported("Only JPEG and PNG evidence files are supported");
        }
        return extension(contentType);
    }

    private boolean matchesSignature(byte[] content, String contentType) {
        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return content.length >= 3 && (content[0] & 0xff) == 0xff
                    && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff;
        }
        return content.length >= 8 && (content[0] & 0xff) == 0x89 && (content[1] & 0xff) == 0x50
                && (content[2] & 0xff) == 0x4e && (content[3] & 0xff) == 0x47
                && (content[4] & 0xff) == 0x0d && (content[5] & 0xff) == 0x0a
                && (content[6] & 0xff) == 0x1a && (content[7] & 0xff) == 0x0a;
    }

    private String extension(String contentType) {
        return "image/png".equalsIgnoreCase(contentType) ? "png" : "jpg";
    }

    private UnsupportedFileTypeException unsupported(String message) {
        return new UnsupportedFileTypeException("EVIDENCE_UNSUPPORTED_FORMAT", message);
    }

    public record ValidatedEvidenceFile(byte[] content, String mimeType, String extension) {
    }
}
