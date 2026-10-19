CREATE TABLE notification_requests (
    notification_id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200),
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(40) NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE notification_outbox (
    event_id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL REFERENCES notification_requests(notification_id),
    event_type VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_outbox_claim
    ON notification_outbox(status, next_attempt_at, created_at);

CREATE TABLE notification_jobs (
    job_id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notification_requests(notification_id),
    tenant_id VARCHAR(100) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    template_id UUID NOT NULL,
    template_version INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    provider_message_id VARCHAR(255),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_notification_job_channel UNIQUE(notification_id, channel),
    CONSTRAINT uk_provider_message UNIQUE(provider_message_id)
);

CREATE TABLE delivery_attempts (
    attempt_id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES notification_jobs(job_id),
    attempt_number INTEGER NOT NULL,
    provider_name VARCHAR(100) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    provider_message_id VARCHAR(255),
    error_message VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_delivery_attempt_number UNIQUE(job_id, attempt_number)
);

CREATE INDEX idx_delivery_attempt_reconciliation
    ON delivery_attempts(outcome, occurred_at);

-- Te tabele stanowią wykonywalny kontrakt trwałości i współbieżności. Runtime
-- pozostaje czytelną symulacją in-memory; adapter produkcyjny implementowałby
-- porty aplikacyjne na podstawie dokładnie tych constraintów i zapytań SKIP LOCKED.
