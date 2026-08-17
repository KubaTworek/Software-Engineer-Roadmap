CREATE TABLE subscription_plans (
    code TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    level INT NOT NULL UNIQUE,
    monthly_price_cents INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO subscription_plans (code, display_name, level, monthly_price_cents) VALUES
('FREE', 'Free', 0, 0),
('BASIC', 'Basic', 10, 999),
('PREMIUM', 'Premium', 20, 1999)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE user_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    plan_code TEXT NOT NULL REFERENCES subscription_plans(code),
    status TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_subscriptions_user_status ON user_subscriptions(user_id, status);
CREATE INDEX idx_user_subscriptions_expires ON user_subscriptions(expires_at);

ALTER TABLE videos ADD COLUMN minimum_plan_code TEXT NOT NULL DEFAULT 'FREE' REFERENCES subscription_plans(code);
ALTER TABLE videos ADD COLUMN allowed_countries TEXT;
ALTER TABLE videos ADD COLUMN drm_protected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE videos ADD COLUMN license_policy TEXT NOT NULL DEFAULT 'STREAMING_ONLY';

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    admin_user_id UUID REFERENCES users(id),
    admin_email TEXT,
    action TEXT NOT NULL,
    resource_type TEXT,
    resource_id TEXT,
    http_method TEXT,
    request_path TEXT,
    status_code INT,
    ip_address TEXT,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_admin_created ON audit_logs(admin_user_id, created_at DESC);
CREATE INDEX idx_audit_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_action_created ON audit_logs(action, created_at DESC);

-- Give the seeded admin a PREMIUM subscription for demo flows.
INSERT INTO user_subscriptions (id, user_id, plan_code, status, started_at, expires_at, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'PREMIUM',
    'ACTIVE',
    now(),
    now() + interval '365 days',
    now(),
    now()
)
ON CONFLICT (id) DO NOTHING;
