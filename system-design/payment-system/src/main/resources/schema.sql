CREATE TABLE merchants (
    merchant_id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    settlement_currency CHAR(3) NOT NULL,
    payout_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE payments (
    payment_id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    order_id VARCHAR(80) NOT NULL,
    customer_id VARCHAR(80),
    amount BIGINT NOT NULL CHECK (amount > 0),
    captured_amount BIGINT NOT NULL DEFAULT 0,
    refunded_amount BIGINT NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    settlement_amount BIGINT,
    settlement_currency CHAR(3),
    fx_rate VARCHAR(40),
    platform_fee_amount BIGINT NOT NULL DEFAULT 0,
    merchant_amount BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(120),
    checkout_url VARCHAR(500),
    capture_mode VARCHAR(20) NOT NULL,
    risk_score INT NOT NULL DEFAULT 0,
    risk_decision VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(provider, provider_payment_id)
);

CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_provider_payment_id ON payments(provider, provider_payment_id);

CREATE TABLE refunds (
    refund_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(120),
    UNIQUE(payment_id, idempotency_key)
);

CREATE TABLE chargebacks (
    chargeback_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    reason VARCHAR(120),
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE payout_batches (
    payout_batch_id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP
);

CREATE TABLE ledger_transactions (
    transaction_id UUID PRIMARY KEY,
    reference_type VARCHAR(80) NOT NULL,
    reference_id UUID NOT NULL,
    transaction_type VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE(reference_type, reference_id, transaction_type)
);

CREATE TABLE ledger_entries (
    entry_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    account_id VARCHAR(160) NOT NULL,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ledger_account ON ledger_entries(account_id);

CREATE TABLE provider_health (
    provider VARCHAR(40) PRIMARY KEY,
    status VARCHAR(40) NOT NULL,
    failure_count INT NOT NULL,
    opened_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(120) NOT NULL,
    scope VARCHAR(120) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_body CLOB NOT NULL,
    http_status INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (idempotency_key, scope)
);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE TABLE audit_logs (
    audit_id UUID PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(120) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(120) NOT NULL,
    details CLOB,
    created_at TIMESTAMP NOT NULL
);
