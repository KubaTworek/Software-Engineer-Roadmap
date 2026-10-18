CREATE TABLE payments (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE,
    order_id UUID NOT NULL UNIQUE,
    user_id VARCHAR(200) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(40) NOT NULL CHECK (status IN ('PAID', 'REFUNDED')),
    refund_idempotency_key VARCHAR(200) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refunded_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
