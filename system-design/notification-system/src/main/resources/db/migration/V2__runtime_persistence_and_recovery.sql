ALTER TABLE notification_requests ADD COLUMN user_id VARCHAR(200);
ALTER TABLE notification_requests ADD COLUMN notification_type VARCHAR(60);
ALTER TABLE notification_requests ADD COLUMN requested_channels TEXT;
ALTER TABLE notification_requests ADD COLUMN selected_channels TEXT;
ALTER TABLE notification_requests ADD COLUMN contact_email VARCHAR(500);
ALTER TABLE notification_requests ADD COLUMN contact_phone VARCHAR(100);
ALTER TABLE notification_requests ADD COLUMN contact_push VARCHAR(1000);
ALTER TABLE notification_requests ADD COLUMN payload TEXT;
ALTER TABLE notification_requests ADD COLUMN deduplication_key VARCHAR(200);
ALTER TABLE notification_requests ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE notification_requests ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE notification_requests ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_notification_deduplication ON notification_requests(tenant_id, deduplication_key);

ALTER TABLE notification_outbox ADD COLUMN last_error VARCHAR(1000);
ALTER TABLE notification_outbox ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE notification_jobs ADD COLUMN user_id VARCHAR(200);
ALTER TABLE notification_jobs ADD COLUMN notification_type VARCHAR(60);
ALTER TABLE notification_jobs ADD COLUMN contact_email VARCHAR(500);
ALTER TABLE notification_jobs ADD COLUMN contact_phone VARCHAR(100);
ALTER TABLE notification_jobs ADD COLUMN contact_push VARCHAR(1000);
ALTER TABLE notification_jobs ADD COLUMN payload TEXT;
ALTER TABLE notification_jobs ADD COLUMN max_attempts INTEGER DEFAULT 3 NOT NULL;
ALTER TABLE notification_jobs ADD COLUMN provider_name VARCHAR(100);
ALTER TABLE notification_jobs ADD COLUMN last_error VARCHAR(1000);
ALTER TABLE notification_jobs ADD COLUMN fallback_used BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE notification_jobs ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE notification_jobs ADD COLUMN created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE notification_jobs ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE notification_jobs ADD COLUMN dead_letter BOOLEAN DEFAULT FALSE NOT NULL;

CREATE TABLE webhook_inbox (
    event_id VARCHAR(255) PRIMARY KEY,
    provider_message_id VARCHAR(255) NOT NULL,
    status VARCHAR(40) NOT NULL,
    reason VARCHAR(1000),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000)
);

CREATE TABLE suppression_entries (
    tenant_id VARCHAR(100) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    contact_hash VARCHAR(64) NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(tenant_id, channel, contact_hash)
);
