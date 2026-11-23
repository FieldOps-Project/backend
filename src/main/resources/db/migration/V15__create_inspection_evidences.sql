-- ============================================================
-- V15: Private inspection evidence metadata (EP-07 / PBI-043 / Issue #22)
-- ============================================================

CREATE TABLE inspection_evidences (
    id                   UUID PRIMARY KEY,
    inspection_id        UUID          NOT NULL,
    response_id          UUID,
    non_conformity_id    UUID,
    type                 VARCHAR(32)   NOT NULL,
    storage_key          VARCHAR(300)  NOT NULL,
    mime_type            VARCHAR(64)   NOT NULL,
    size_bytes           BIGINT        NOT NULL CHECK (size_bytes > 0),
    checksum             VARCHAR(64)   NOT NULL,
    description          VARCHAR(2000),
    latitude             NUMERIC(9, 6),
    longitude            NUMERIC(9, 6),
    accuracy_meters      NUMERIC(10, 2),
    captured_at_device   TIMESTAMPTZ   NOT NULL,
    server_received_at   TIMESTAMPTZ   NOT NULL,
    uploaded_at          TIMESTAMPTZ   NOT NULL,
    created_by           UUID          NOT NULL,
    deleted_at           TIMESTAMPTZ,
    deleted_by           UUID,
    version              INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_evidence_inspection FOREIGN KEY (inspection_id) REFERENCES inspections(id),
    CONSTRAINT fk_evidence_response FOREIGN KEY (response_id) REFERENCES inspection_responses(id),
    CONSTRAINT fk_evidence_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_evidence_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id),
    CONSTRAINT ck_evidence_type CHECK (length(trim(type)) > 0),
    CONSTRAINT ck_evidence_deleted_pair CHECK ((deleted_at IS NULL) = (deleted_by IS NULL)),
    CONSTRAINT ck_evidence_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    )
);

CREATE UNIQUE INDEX ux_evidence_storage_key ON inspection_evidences (storage_key);
CREATE INDEX ix_evidence_inspection ON inspection_evidences (inspection_id, deleted_at);
CREATE INDEX ix_evidence_response ON inspection_evidences (response_id);
CREATE INDEX ix_evidence_non_conformity ON inspection_evidences (non_conformity_id);
CREATE INDEX ix_evidence_created_by ON inspection_evidences (created_by);
