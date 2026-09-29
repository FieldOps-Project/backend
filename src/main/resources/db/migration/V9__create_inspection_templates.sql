-- ============================================================
-- V9: Inspection template model (EP-04 / PBI-018 / Issue #12)
-- Tables: inspection_templates, inspection_template_versions,
--         template_sections, template_items
-- ============================================================

-- 1. inspection_templates — identidade logica do modelo, mutavel
CREATE TABLE inspection_templates (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title           VARCHAR(255)  NOT NULL,
    description     VARCHAR(1000),
    category        VARCHAR(100)  NOT NULL,
    status          VARCHAR(32)   NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    current_version INTEGER,
    created_by      UUID          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_template_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX ix_templates_status   ON inspection_templates (status);
CREATE INDEX ix_templates_category ON inspection_templates (category);
CREATE INDEX ix_templates_created  ON inspection_templates (created_by);

-- 2. inspection_template_versions — publicacao imutavel, numerada
--    version_number = 0 e published_at NULL representam o rascunho de trabalho (ver ADR)
CREATE TABLE inspection_template_versions (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_id                 UUID        NOT NULL,
    version_number              INTEGER     NOT NULL DEFAULT 0,
    title_snapshot              VARCHAR(255) NOT NULL,
    description_snapshot        VARCHAR(1000),
    published_by                UUID,
    published_at                TIMESTAMPTZ,
    active_for_new_inspections  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_version_template    FOREIGN KEY (template_id)  REFERENCES inspection_templates(id),
    CONSTRAINT fk_version_published   FOREIGN KEY (published_by) REFERENCES users(id)
);

CREATE UNIQUE INDEX ux_tpl_version        ON inspection_template_versions (template_id, version_number);
CREATE INDEX ix_tpl_version_template      ON inspection_template_versions (template_id);
CREATE INDEX ix_tpl_version_active        ON inspection_template_versions (active_for_new_inspections) WHERE active_for_new_inspections = TRUE;

-- 3. template_sections — pertence a versao, nunca ao modelo logico
CREATE TABLE template_sections (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_version_id   UUID         NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    description           VARCHAR(1000),
    display_order         INTEGER      NOT NULL,
    CONSTRAINT fk_section_version FOREIGN KEY (template_version_id)
        REFERENCES inspection_template_versions(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_section_order ON template_sections (template_version_id, display_order);
CREATE INDEX ix_section_version      ON template_sections (template_version_id);

-- 4. template_items — pertence a secao
CREATE TABLE template_items (
    id                                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    section_id                        UUID         NOT NULL,
    code                              VARCHAR(50),
    title                             VARCHAR(255) NOT NULL,
    description                       VARCHAR(1000),
    response_type                     VARCHAR(32)  NOT NULL
        CHECK (response_type IN (
            'TEXT_SHORT', 'TEXT_LONG', 'NUMBER', 'BOOLEAN',
            'CONFORMITY', 'SINGLE_CHOICE', 'DATE'
        )),
    required                          BOOLEAN      NOT NULL DEFAULT TRUE,
    observation_required_on_failure   BOOLEAN      NOT NULL DEFAULT FALSE,
    evidence_required_on_failure      BOOLEAN      NOT NULL DEFAULT FALSE,
    options_json                      TEXT,
    display_order                     INTEGER      NOT NULL,
    CONSTRAINT fk_item_section FOREIGN KEY (section_id)
        REFERENCES template_sections(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_item_order ON template_items (section_id, display_order);
CREATE INDEX ix_item_section      ON template_items (section_id);
