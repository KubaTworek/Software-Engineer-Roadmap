CREATE TABLE payments (
    payment_id UUID PRIMARY KEY,
    order_id VARCHAR(80) NOT NULL,
    customer_id VARCHAR(80),
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(120),
    checkout_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_status_created_at ON payments(status, created_at);
CREATE INDEX idx_payments_provider_payment_id ON payments(provider, provider_payment_id);
