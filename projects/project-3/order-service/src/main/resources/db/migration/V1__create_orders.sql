CREATE TABLE orders (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    degradation_reason VARCHAR(500)
);

CREATE UNIQUE INDEX ux_orders_idempotency_key ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_orders_reservation_id ON orders(reservation_id);
CREATE INDEX idx_orders_user_id ON orders(user_id);
