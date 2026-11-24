-- ============================================================
-- V20: Inspection timeline columns and append-only audit trail (EP-09 / PBI-063 / Issue #29)
-- ============================================================

ALTER TABLE audit_events
    ADD COLUMN inspection_id      UUID REFERENCES inspections(id),
    ADD COLUMN device_occurred_at TIMESTAMPTZ,
    ADD COLUMN request_id         TEXT,
    ADD COLUMN device_id          UUID;

-- Links the events recorded before this migration to their inspection.
UPDATE audit_events SET inspection_id = entity_id
WHERE entity_type = 'INSPECTION';
UPDATE audit_events a SET inspection_id = r.inspection_id
FROM inspection_responses r WHERE a.entity_type = 'INSPECTION_RESPONSE' AND a.entity_id = r.id;
UPDATE audit_events a SET inspection_id = e.inspection_id
FROM inspection_evidences e WHERE a.entity_type = 'INSPECTION_EVIDENCE' AND a.entity_id = e.id;
UPDATE audit_events a SET inspection_id = n.inspection_id
FROM non_conformities n WHERE a.entity_type = 'NON_CONFORMITY' AND a.entity_id = n.id;

CREATE INDEX ix_audit_insp ON audit_events (inspection_id);
CREATE INDEX ix_audit_occurred ON audit_events (occurred_at);
CREATE INDEX ix_audit_request ON audit_events (request_id);

-- Audit events are append-only (RN-088): the application can insert, never change or remove.
CREATE FUNCTION fn_audit_events_append_only() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only'
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER trg_audit_events_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION fn_audit_events_append_only();

CREATE TRIGGER trg_audit_events_no_truncate
    BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION fn_audit_events_append_only();
