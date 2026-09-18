CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255),
    document VARCHAR(14),
    email VARCHAR(320),
    phone VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_clients_document_format CHECK (
        document IS NULL OR (document ~ '^[0-9]+$' AND length(document) IN (11, 14))
    )
);

CREATE INDEX ix_clients_status ON clients (status);
CREATE INDEX ix_clients_name ON clients (lower(name));
CREATE INDEX ix_clients_legal_name ON clients (lower(legal_name));
