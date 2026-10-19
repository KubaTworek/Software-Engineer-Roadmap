CREATE TABLE IF NOT EXISTS rate_limiter_config_head (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    version BIGINT NOT NULL
);
INSERT INTO rate_limiter_config_head(singleton_id, version)
VALUES (1, 0) ON CONFLICT (singleton_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS rate_limiter_rule_change (
    version BIGINT PRIMARY KEY,
    rule_id VARCHAR(200) NOT NULL,
    rule_json TEXT NOT NULL,
    actor VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rate_limiter_usage_receipt (
    event_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    operation_cost BIGINT NOT NULL CHECK (operation_cost > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    payload TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS rate_limiter_usage_outbox (
    event_id UUID PRIMARY KEY REFERENCES rate_limiter_usage_receipt(event_id),
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    owner VARCHAR(200),
    lease_until TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(500)
);

-- Idempotent evolution keeps an existing educational volume usable.
ALTER TABLE rate_limiter_usage_outbox
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE rate_limiter_usage_outbox
    ADD COLUMN IF NOT EXISTS last_error VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_rate_limiter_outbox_claim
    ON rate_limiter_usage_outbox(state, lease_until, event_id);
CREATE INDEX IF NOT EXISTS idx_rate_limiter_usage_tenant_time
    ON rate_limiter_usage_receipt(tenant_id, occurred_at);
