CREATE TABLE merchants (
    merchant_id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    settlement_currency VARCHAR(3) NOT NULL,
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
    currency VARCHAR(3) NOT NULL,
    settlement_amount BIGINT,
    settlement_currency VARCHAR(3),
    fx_rate VARCHAR(40),
    platform_fee_amount BIGINT NOT NULL DEFAULT 0,
    merchant_amount BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(120),
    checkout_url VARCHAR(500),
    capture_mode VARCHAR(20) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    risk_score INT NOT NULL DEFAULT 0,
    risk_decision VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id),
    CONSTRAINT ck_payment_amounts CHECK (
        captured_amount >= 0 AND captured_amount <= amount
        AND refunded_amount >= 0 AND refunded_amount <= captured_amount
        AND platform_fee_amount >= 0 AND merchant_amount >= 0
    ),
    UNIQUE(provider, provider_payment_id)
);

CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_provider_payment_id ON payments(provider, provider_payment_id);

CREATE TABLE payment_attempts (
    attempt_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_idempotency_key VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_payment_id VARCHAR(200),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_attempt_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id),
    CONSTRAINT uk_attempt_provider_key UNIQUE(provider, provider_idempotency_key)
);

CREATE TABLE provider_operations (
    operation_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_idempotency_key VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_reference VARCHAR(200),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_provider_operation_key UNIQUE(provider, operation_type, provider_idempotency_key)
);

CREATE INDEX idx_provider_operation_aggregate ON provider_operations(aggregate_id, operation_type);

CREATE TABLE refunds (
    refund_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(120),
    provider_refund_id VARCHAR(200),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id),
    UNIQUE(payment_id, idempotency_key)
);

CREATE TABLE chargebacks (
    chargeback_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(120),
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    provider_dispute_id VARCHAR(200) NOT NULL,
    CONSTRAINT fk_chargeback_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id),
    CONSTRAINT uk_chargeback_provider_dispute UNIQUE(provider_dispute_id)
);

CREATE TABLE payout_batches (
    payout_batch_id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP,
    idempotency_key VARCHAR(200) NOT NULL,
    provider_payout_id VARCHAR(200),
    CONSTRAINT fk_payout_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id),
    CONSTRAINT uk_payout_merchant_key UNIQUE(merchant_id, idempotency_key)
);

CREATE TABLE captures (
    capture_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_capture_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id),
    CONSTRAINT uk_capture_payment_key UNIQUE(payment_id, idempotency_key)
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
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_transaction FOREIGN KEY (transaction_id) REFERENCES ledger_transactions(transaction_id)
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
    response_body TEXT,
    http_status INT,
    status VARCHAR(30) NOT NULL,
    resource_id VARCHAR(200),
    lease_until TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (idempotency_key, scope)
);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    last_error VARCHAR(1000),
    dead_lettered_at TIMESTAMP,
    event_version INT NOT NULL DEFAULT 1,
    correlation_id VARCHAR(100)
);

CREATE INDEX idx_outbox_pending ON outbox_events(published_at, dead_lettered_at, next_attempt_at, created_at);

CREATE TABLE webhook_events (
    webhook_event_id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    provider_payment_id VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(1000),
    occurred_at TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT uk_webhook_provider_event UNIQUE(provider, provider_event_id)
);

CREATE INDEX idx_webhook_pending ON webhook_events(status, received_at);

CREATE TABLE reconciliation_breaks (
    reconciliation_break_id UUID PRIMARY KEY,
    payment_id UUID,
    provider VARCHAR(50) NOT NULL,
    provider_payment_id VARCHAR(200) NOT NULL,
    break_type VARCHAR(50) NOT NULL,
    expected_value VARCHAR(200),
    actual_value VARCHAR(200),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT fk_reconciliation_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id)
);

CREATE TABLE settlement_reports (
    report_id VARCHAR(260) PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    external_report_id VARCHAR(200) NOT NULL,
    period_from TIMESTAMP NOT NULL,
    period_to TIMESTAMP NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    imported_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT uk_settlement_report_provider_external UNIQUE(provider, external_report_id)
);

CREATE TABLE audit_logs (
    audit_id UUID PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(120) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(120) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE approval_requests (
    approval_id UUID PRIMARY KEY,
    operation VARCHAR(40) NOT NULL,
    target_id VARCHAR(200) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    approved_at TIMESTAMP,
    consumed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
