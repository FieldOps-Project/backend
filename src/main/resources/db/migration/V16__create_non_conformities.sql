-- V16: Inspection non-conformities with offline-safe ids and evidence linkage (Issue #23)

CREATE TABLE non_conformities (
    id                   UUID PRIMARY KEY,
    inspection_id        UUID          NOT NULL,
    inspection_item_id   UUID,
    response_id          UUID,
    title                VARCHAR(255)  NOT NULL,
    description          VARCHAR(2000) NOT NULL,
    severity             VARCHAR(16)   NOT NULL,
    status               VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    created_by           UUID          NOT NULL,
    created_at_device    TIMESTAMPTZ   NOT NULL,
    server_received_at   TIMESTAMPTZ   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_nc_inspection FOREIGN KEY (inspection_id) REFERENCES inspections(id),
    CONSTRAINT fk_nc_snapshot FOREIGN KEY (inspection_item_id, inspection_id)
        REFERENCES inspection_item_snapshots(id, inspection_id),
    CONSTRAINT fk_nc_response FOREIGN KEY (response_id) REFERENCES inspection_responses(id),
    CONSTRAINT fk_nc_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT ck_nc_title CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_nc_description CHECK (length(trim(description)) > 0),
    CONSTRAINT ck_nc_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_nc_status CHECK (status = 'OPEN')
);

CREATE INDEX ix_nc_inspection ON non_conformities (inspection_id);
CREATE INDEX ix_nc_severity ON non_conformities (severity);
CREATE INDEX ix_nc_status ON non_conformities (status);
CREATE INDEX ix_nc_response ON non_conformities (response_id);

ALTER TABLE inspection_evidences
    ADD CONSTRAINT fk_evidence_non_conformity
    FOREIGN KEY (non_conformity_id) REFERENCES non_conformities(id);
