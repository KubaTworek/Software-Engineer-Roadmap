-- Nie modyfikujemy V1: wdrożone migracje są niezmienne, inaczej Flyway wykryje
-- checksum mismatch. Ta migracja jest addytywnym krokiem expand.
ALTER TABLE orders ADD COLUMN request_fingerprint VARCHAR(255);

CREATE TABLE outbox_messages (
    id UUID PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_messages(occurred_at)
    WHERE published_at IS NULL;
