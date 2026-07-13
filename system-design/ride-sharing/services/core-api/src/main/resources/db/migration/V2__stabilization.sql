CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    idem_key VARCHAR(255) NOT NULL,
    user_id UUID REFERENCES app_users(id),
    endpoint VARCHAR(255) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_body TEXT,
    http_status INTEGER,
    status VARCHAR(40) NOT NULL,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_idempotency_key UNIQUE (idem_key, user_id, endpoint)
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_keys(expires_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    topic VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);

CREATE TABLE support_tickets (
    id UUID PRIMARY KEY,
    ride_id UUID REFERENCES rides(id),
    reporter_id UUID REFERENCES app_users(id),
    assigned_admin_id UUID REFERENCES app_users(id),
    category VARCHAR(80) NOT NULL,
    priority VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    resolution TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP
);

CREATE INDEX idx_support_tickets_status ON support_tickets(status);
CREATE INDEX idx_support_tickets_ride ON support_tickets(ride_id);
CREATE INDEX idx_support_tickets_reporter ON support_tickets(reporter_id);

CREATE TABLE ride_status_history (
    id UUID PRIMARY KEY,
    ride_id UUID NOT NULL REFERENCES rides(id),
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id UUID,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ride_status_history_ride ON ride_status_history(ride_id);
