-- V18: Stable cursor indexes and logical-delete timestamps for mobile pull.

ALTER TABLE inspection_evidences
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX ix_clients_sync_cursor
    ON clients (updated_at, id);
CREATE INDEX ix_sites_sync_cursor
    ON inspection_sites (updated_at, id);
CREATE INDEX ix_equipment_sync_cursor
    ON equipment (updated_at, id);
CREATE INDEX ix_inspections_sync_cursor
    ON inspections (updated_at, id);
CREATE INDEX ix_snapshots_sync_cursor
    ON inspection_item_snapshots (created_at, id);
CREATE INDEX ix_responses_sync_cursor
    ON inspection_responses (updated_at, id);
CREATE INDEX ix_evidences_sync_cursor
    ON inspection_evidences (updated_at, id);
CREATE INDEX ix_non_conformities_sync_cursor
    ON non_conformities (updated_at, id);
