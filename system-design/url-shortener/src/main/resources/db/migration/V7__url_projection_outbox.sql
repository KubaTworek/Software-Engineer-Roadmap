CREATE TABLE url_projection_outbox (
    event_id VARCHAR(36) PRIMARY KEY,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_url_projection_outbox_pending
    ON url_projection_outbox(sent_at, created_at);
