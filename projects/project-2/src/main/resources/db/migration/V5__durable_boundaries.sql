CREATE TABLE ordering.api_idempotency_keys (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    response_id UUID NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE inventory.stock_items ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payment.payments ADD COLUMN provider_operation_id VARCHAR(128);
ALTER TABLE payment.payments ADD COLUMN uncertainty_reason TEXT;
