ALTER TABLE webhook_inbox ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE webhook_inbox ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE webhook_inbox ADD COLUMN dead BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_webhook_inbox_retry
    ON webhook_inbox(processed_at, dead, next_attempt_at);

CREATE INDEX idx_notification_jobs_due_claim
    ON notification_jobs(status, next_attempt_at, lease_until, priority);

CREATE TABLE notification_campaigns (
    campaign_id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    notification_type VARCHAR(60) NOT NULL,
    user_ids TEXT NOT NULL,
    channels TEXT NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    next_recipient_index INTEGER NOT NULL,
    failed_recipients INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_campaign_tenant_created ON notification_campaigns(tenant_id, created_at);

CREATE TABLE notification_digest_buffers (
    digest_id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(200) NOT NULL,
    digest_key VARCHAR(200) NOT NULL,
    items TEXT NOT NULL,
    flush_at TIMESTAMP WITH TIME ZONE NOT NULL,
    flushed BOOLEAN NOT NULL DEFAULT FALSE,
    claimed BOOLEAN NOT NULL DEFAULT FALSE,
    claim_until TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_digest_due ON notification_digest_buffers(flushed, flush_at, claim_until);
