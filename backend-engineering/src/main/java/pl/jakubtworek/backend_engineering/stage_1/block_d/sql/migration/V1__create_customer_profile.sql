CREATE TABLE customer_profile (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL CHECK (btrim(first_name) <> ''),
    last_name VARCHAR(100) NOT NULL CHECK (btrim(last_name) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
