CREATE TABLE push_devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id VARCHAR(255) NOT NULL,
    push_token VARCHAR(512) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ux_push_devices_user_device UNIQUE (user_id, device_id)
);

CREATE INDEX ix_push_devices_user ON push_devices (user_id);
