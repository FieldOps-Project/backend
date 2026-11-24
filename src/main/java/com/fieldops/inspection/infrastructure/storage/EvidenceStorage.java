package com.fieldops.inspection.infrastructure.storage;

public interface EvidenceStorage {

    void store(String storageKey, byte[] content);

    /** Writes the object even when the key already exists, so a resent file never leaves a partial object. */
    void replace(String storageKey, byte[] content);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
