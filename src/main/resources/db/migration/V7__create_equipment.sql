CREATE TABLE equipment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    site_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    asset_number VARCHAR(100),
    serial_number VARCHAR(100),
    manufacturer VARCHAR(255),
    model VARCHAR(255),
    description VARCHAR(1000),
    qr_code VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DECOMMISSIONED')),
    installed_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_equipment_site FOREIGN KEY (site_id) REFERENCES inspection_sites(id)
);

CREATE INDEX ix_equipment_site ON equipment (site_id);
CREATE INDEX ix_equipment_status ON equipment (status);
CREATE INDEX ix_equipment_qr ON equipment (qr_code);
CREATE INDEX ix_equipment_name ON equipment (name);
