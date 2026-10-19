ALTER TABLE outbox_messages ADD COLUMN dead_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_outbox_ready
    ON outbox_messages(next_attempt_at, occurred_at)
    WHERE published_at IS NULL AND dead_at IS NULL;
