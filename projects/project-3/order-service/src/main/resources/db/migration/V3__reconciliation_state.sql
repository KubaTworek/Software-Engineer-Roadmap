ALTER TABLE orders ADD COLUMN reconciliation_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_orders_reconciliation ON orders(next_attempt_at)
    WHERE status IN ('PAYMENT_UNKNOWN', 'RESERVATION_CONFIRMATION_PENDING');

ALTER TABLE outbox_messages ADD COLUMN publish_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox_messages ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_messages ADD COLUMN last_error VARCHAR(1000);
