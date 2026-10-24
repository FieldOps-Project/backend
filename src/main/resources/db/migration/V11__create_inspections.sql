-- ============================================================
-- V11: Inspection execution model (EP-05 / PBI-024 / Issue #15)
-- Tables: inspections, inspection_item_snapshots
-- ============================================================

-- Composite keys so an inspection can prove, in the database, that its site
-- belongs to its client and its equipment belongs to its site (RN-014).
ALTER TABLE inspection_sites ADD CONSTRAINT ux_sites_id_client UNIQUE (id, client_id);
ALTER TABLE equipment        ADD CONSTRAINT ux_equipment_id_site UNIQUE (id, site_id);

-- 1. inspections — uma execucao real de um checklist publicado
--    Pares *_at_device / *_at_server: relogio do aparelho no momento real da
--    acao e instante em que o servidor recebeu (RN-034, RN-074).
CREATE TABLE inspections (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_version_id  UUID          NOT NULL,
    client_id            UUID          NOT NULL,
    site_id              UUID          NOT NULL,
    equipment_id         UUID,
    technician_id        UUID          NOT NULL,
    supervisor_id        UUID,
    created_by           UUID          NOT NULL,
    title                VARCHAR(255)  NOT NULL,
    instructions         VARCHAR(2000),
    priority             VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM'
                             CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status               VARCHAR(32)   NOT NULL
                             CHECK (status IN (
                                 'DRAFT', 'ASSIGNED', 'IN_PROGRESS', 'SUBMITTED',
                                 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'CANCELED'
                             )),
    scheduled_for        TIMESTAMPTZ   NOT NULL,
    started_at_device    TIMESTAMPTZ,
    started_at_server    TIMESTAMPTZ,
    completed_at_device  TIMESTAMPTZ,
    submitted_at_server  TIMESTAMPTZ,
    approved_at          TIMESTAMPTZ,
    canceled_at          TIMESTAMPTZ,
    canceled_by          UUID,
    canceled_reason      VARCHAR(1000),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_inspection_version     FOREIGN KEY (template_version_id) REFERENCES inspection_template_versions(id),
    CONSTRAINT fk_inspection_client      FOREIGN KEY (client_id)     REFERENCES clients(id),
    CONSTRAINT fk_inspection_site_client FOREIGN KEY (site_id, client_id) REFERENCES inspection_sites(id, client_id),
    CONSTRAINT fk_inspection_equipment_site FOREIGN KEY (equipment_id, site_id) REFERENCES equipment(id, site_id),
    CONSTRAINT fk_inspection_technician  FOREIGN KEY (technician_id) REFERENCES users(id),
    CONSTRAINT fk_inspection_supervisor  FOREIGN KEY (supervisor_id) REFERENCES users(id),
    CONSTRAINT fk_inspection_created_by  FOREIGN KEY (created_by)    REFERENCES users(id),
    CONSTRAINT fk_inspection_canceled_by FOREIGN KEY (canceled_by)   REFERENCES users(id),
    CONSTRAINT ck_inspection_title CHECK (length(trim(title)) > 0),
    -- Cancelamento exige data, autor e motivo; e so existe no estado CANCELED.
    CONSTRAINT ck_inspection_canceled CHECK (
        (status = 'CANCELED') = (canceled_at IS NOT NULL)
        AND (canceled_at IS NULL OR (
            canceled_by IS NOT NULL
            AND canceled_reason IS NOT NULL
            AND length(trim(canceled_reason)) > 0
        ))
    ),
    CONSTRAINT ck_inspection_approved CHECK (status <> 'APPROVED' OR approved_at IS NOT NULL),
    -- O servidor so registra o recebimento de uma acao que o aparelho registrou.
    CONSTRAINT ck_inspection_started_pair   CHECK (started_at_server IS NULL OR started_at_device IS NOT NULL),
    CONSTRAINT ck_inspection_submitted_pair CHECK (submitted_at_server IS NULL OR completed_at_device IS NOT NULL)
);

CREATE INDEX ix_insp_tech_status   ON inspections (technician_id, status);
CREATE INDEX ix_insp_sup_status    ON inspections (supervisor_id, status);
CREATE INDEX ix_insp_client        ON inspections (client_id);
CREATE INDEX ix_insp_site          ON inspections (site_id);
CREATE INDEX ix_insp_equipment     ON inspections (equipment_id);
CREATE INDEX ix_insp_scheduled     ON inspections (scheduled_for);
CREATE INDEX ix_insp_status_sched  ON inspections (status, scheduled_for);

-- Uma inspecao so pode usar versao publicada (RN-024); o rascunho e
-- version_number = 0 com published_at NULL (ADR-001).
CREATE FUNCTION fn_inspection_requires_published_version() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM inspection_template_versions
        WHERE id = NEW.template_version_id AND published_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'inspection must reference a published template version'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_inspection_published_version';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inspection_published_version
    BEFORE INSERT OR UPDATE OF template_version_id ON inspections
    FOR EACH ROW EXECUTE FUNCTION fn_inspection_requires_published_version();

-- 2. inspection_item_snapshots — copia do texto e das regras de cada item
--    source_template_item_id e apenas rastro historico; o texto exibido e
--    sempre o daqui (RN-021).
CREATE TABLE inspection_item_snapshots (
    id                       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    inspection_id            UUID          NOT NULL,
    source_template_item_id  UUID,
    section_title            VARCHAR(255)  NOT NULL,
    section_description      VARCHAR(1000),
    section_order            INTEGER       NOT NULL CHECK (section_order >= 1),
    item_code                VARCHAR(50),
    item_title               VARCHAR(255)  NOT NULL,
    item_description         VARCHAR(1000),
    response_type            VARCHAR(32)   NOT NULL
        CHECK (response_type IN (
            'TEXT_SHORT', 'TEXT_LONG', 'NUMBER', 'BOOLEAN',
            'CONFORMITY', 'SINGLE_CHOICE', 'DATE'
        )),
    required                 BOOLEAN       NOT NULL,
    rules_json               JSONB         NOT NULL,
    options_json             JSONB,
    item_order               INTEGER       NOT NULL CHECK (item_order >= 1),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_snapshot_inspection  FOREIGN KEY (inspection_id) REFERENCES inspections(id),
    CONSTRAINT fk_snapshot_source_item FOREIGN KEY (source_template_item_id) REFERENCES template_items(id)
);

CREATE INDEX ix_snap_inspection ON inspection_item_snapshots (inspection_id);
CREATE UNIQUE INDEX ix_snap_order ON inspection_item_snapshots (inspection_id, section_order, item_order);

-- O snapshot e imutavel: corrigir um texto exige uma nova versao do modelo.
CREATE FUNCTION fn_snapshot_is_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'inspection item snapshots are immutable'
        USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_snapshot_immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_snapshot_immutable
    BEFORE UPDATE ON inspection_item_snapshots
    FOR EACH ROW EXECUTE FUNCTION fn_snapshot_is_immutable();
