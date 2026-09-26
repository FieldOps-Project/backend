CREATE TABLE inspection_sites (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    address_line VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    contact_name VARCHAR(255),
    contact_phone VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_inspection_sites_client FOREIGN KEY (client_id) REFERENCES clients(id),
    CONSTRAINT ck_inspection_sites_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (
            latitude IS NOT NULL AND longitude IS NOT NULL
            AND latitude BETWEEN -90 AND 90
            AND longitude BETWEEN -180 AND 180
        )
    )
);

CREATE INDEX ix_sites_client ON inspection_sites (client_id);
CREATE INDEX ix_sites_status ON inspection_sites (status);
