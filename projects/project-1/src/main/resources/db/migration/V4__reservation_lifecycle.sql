ALTER TABLE reservations
    ADD COLUMN expires_at TIMESTAMPTZ;

UPDATE reservations
   SET expires_at = created_at + INTERVAL '15 minutes'
 WHERE expires_at IS NULL;

ALTER TABLE reservations
    ALTER COLUMN expires_at SET NOT NULL;

ALTER TABLE reservations
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE capacity_pools
    ADD CONSTRAINT chk_capacity_within_total
    CHECK (available_capacity <= total_capacity);

ALTER TABLE reservations
    ADD CONSTRAINT chk_reservation_timestamps
    CHECK (
        (status = 'CONFIRMED' AND confirmed_at IS NOT NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR status IN ('PENDING', 'EXPIRED', 'PAYMENT_TIMEOUT')
    );

CREATE INDEX idx_reservations_expiration_queue
    ON reservations(expires_at, id)
    WHERE status IN ('PENDING', 'PAYMENT_TIMEOUT');
