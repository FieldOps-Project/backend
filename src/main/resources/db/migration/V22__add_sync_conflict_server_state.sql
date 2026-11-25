-- V22: Persist server state snapshot for conflicted sync operations (Issue #26)

ALTER TABLE sync_processed_operations
    ADD COLUMN server_state JSONB;
