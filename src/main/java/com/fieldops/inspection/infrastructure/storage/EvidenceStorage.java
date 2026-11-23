package com.fieldops.inspection.infrastructure.storage;

public interface EvidenceStorage {

    void store(String storageKey, byte[] content);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
