ALTER TABLE urls ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_idempotency_records_expires_at
    ON idempotency_records(expires_at);
