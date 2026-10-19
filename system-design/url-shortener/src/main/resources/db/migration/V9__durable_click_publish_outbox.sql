CREATE TABLE click_publish_outbox (
    event_id VARCHAR(36) PRIMARY KEY,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(512)
);

CREATE INDEX idx_click_publish_outbox_pending
    ON click_publish_outbox(published_at, created_at);
