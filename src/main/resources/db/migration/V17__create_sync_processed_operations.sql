-- V17: Persistent idempotency records for mobile synchronization (Issue #24)

CREATE TABLE sync_processed_operations (
    operation_id   UUID PRIMARY KEY,
    device_id      UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    entity_type    VARCHAR(64)  NOT NULL,
    entity_id      UUID         NOT NULL,
    result_status  VARCHAR(32)  NOT NULL,
    entity_version INTEGER,
    result_code    VARCHAR(100),
    result_message VARCHAR(500),
    processed_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_sync_operation_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_sync_operation_status CHECK (
        result_status IN ('APPLIED', 'CONFLICT', 'REJECTED', 'DEPENDENCY_FAILED')
    )
);

CREATE INDEX ix_sync_operations_user ON sync_processed_operations (user_id, processed_at);
CREATE INDEX ix_sync_operations_entity ON sync_processed_operations (entity_type, entity_id);
