CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    consumer_name VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE storage_blobs
    ADD COLUMN encrypted_data_key TEXT,
    ADD COLUMN key_version VARCHAR(80);
