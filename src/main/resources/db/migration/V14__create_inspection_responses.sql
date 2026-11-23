-- ============================================================
-- V14: Typed inspection responses (EP-06 / PBI-036 / Issue #21)
-- ============================================================

ALTER TABLE inspection_item_snapshots
    ADD CONSTRAINT ux_snapshot_id_inspection UNIQUE (id, inspection_id);

CREATE TABLE inspection_responses (
    id                    UUID PRIMARY KEY,
    inspection_id         UUID          NOT NULL,
    inspection_item_id    UUID          NOT NULL,
    answered_by           UUID          NOT NULL,
    value_text            VARCHAR(2000),
    value_number          NUMERIC(24, 8),
    value_boolean         BOOLEAN,
    value_date            DATE,
    value_json            JSONB,
    conformity            VARCHAR(20),
    observation           VARCHAR(2000),
    answered_at_device    TIMESTAMPTZ   NOT NULL,
    answered_at_server    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    base_version          INTEGER       NOT NULL DEFAULT 0 CHECK (base_version >= 0),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_response_inspection FOREIGN KEY (inspection_id) REFERENCES inspections(id),
    CONSTRAINT fk_response_snapshot FOREIGN KEY (inspection_item_id, inspection_id)
        REFERENCES inspection_item_snapshots(id, inspection_id),
    CONSTRAINT fk_response_answered_by FOREIGN KEY (answered_by) REFERENCES users(id),
    CONSTRAINT ck_response_conformity CHECK (
        conformity IS NULL OR conformity IN ('NOT_APPLICABLE', 'CONFORMING', 'NON_CONFORMING')
    ),
    CONSTRAINT ck_response_has_value CHECK (
        value_text IS NOT NULL OR value_number IS NOT NULL OR value_boolean IS NOT NULL
        OR value_date IS NOT NULL OR value_json IS NOT NULL OR conformity IS NOT NULL
    )
);

CREATE UNIQUE INDEX ux_response_item ON inspection_responses (inspection_item_id);
CREATE INDEX ix_response_inspection ON inspection_responses (inspection_id);
CREATE INDEX ix_response_answered_by ON inspection_responses (answered_by);
