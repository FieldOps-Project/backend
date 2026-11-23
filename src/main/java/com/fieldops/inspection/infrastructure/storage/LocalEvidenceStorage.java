package com.fieldops.inspection.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Component
public class LocalEvidenceStorage implements EvidenceStorage {

    private final Path root;

    public LocalEvidenceStorage(
            @Value("${app.evidence.storage-dir:${java.io.tmpdir}/fieldops-evidence}") String storageDirectory
    ) {
        this.root = Paths.get(storageDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void store(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException ignored) {
                // The database idempotency key already owns this object.
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist evidence object", exception);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read evidence object", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete evidence object", exception);
        }
    }

    private Path resolve(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Evidence storage key escapes the configured directory");
        }
        return target;
    }
}
