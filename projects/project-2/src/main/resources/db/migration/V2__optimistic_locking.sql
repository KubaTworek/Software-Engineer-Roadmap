ALTER TABLE ordering.orders
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN ordering.orders.version IS
    'Optimistic-lock token; updates must include the previously read value in WHERE';

ALTER TABLE integration.outbox_events
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_outbox_claim_candidates
    ON integration.outbox_events(status, created_at);

ALTER TABLE fulfillment.shipments
    ADD CONSTRAINT uq_shipments_order_id UNIQUE (order_id);
