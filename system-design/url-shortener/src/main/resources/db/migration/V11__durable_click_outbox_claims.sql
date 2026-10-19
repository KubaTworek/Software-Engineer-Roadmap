ALTER TABLE click_publish_outbox
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE click_publish_outbox
    ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;

ALTER TABLE click_publish_outbox
    ADD COLUMN dead_lettered_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_click_publish_outbox_claimable
    ON click_publish_outbox (published_at, dead_lettered_at, next_attempt_at, locked_until, created_at);
