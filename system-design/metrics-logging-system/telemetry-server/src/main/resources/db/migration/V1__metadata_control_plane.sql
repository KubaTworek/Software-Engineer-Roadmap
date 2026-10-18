CREATE TABLE tenants (
    tenant_id varchar(100) PRIMARY KEY,
    display_name varchar(250) NOT NULL,
    status varchar(32) NOT NULL,
    plan varchar(64) NOT NULL,
    primary_region varchar(64) NOT NULL,
    retention_days integer NOT NULL CHECK (retention_days > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE tenant_api_keys (
    key_id varchar(100) PRIMARY KEY,
    tenant_id varchar(100) NOT NULL REFERENCES tenants(tenant_id),
    token_hash char(64) NOT NULL UNIQUE,
    name varchar(250) NOT NULL,
    roles text[] NOT NULL,
    status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz
);

CREATE TABLE alert_rules (
    rule_id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL REFERENCES tenants(tenant_id),
    definition_json text NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_alert_rules_tenant ON alert_rules(tenant_id);

CREATE TABLE alert_states (
    rule_id uuid PRIMARY KEY REFERENCES alert_rules(rule_id) ON DELETE CASCADE,
    tenant_id varchar(100) NOT NULL,
    state varchar(32) NOT NULL,
    condition_since timestamptz,
    last_evaluated_at timestamptz NOT NULL,
    last_notified_at timestamptz,
    observed_value double precision NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE alert_silences (
    silence_id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL REFERENCES tenants(tenant_id),
    rule_id uuid REFERENCES alert_rules(rule_id) ON DELETE CASCADE,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    reason varchar(1000) NOT NULL,
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_active_silences ON alert_silences(tenant_id, starts_at, ends_at);

CREATE TABLE notification_outbox (
    delivery_id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    rule_id uuid NOT NULL,
    channel_type varchar(64) NOT NULL,
    target varchar(2000) NOT NULL,
    payload_json text NOT NULL,
    status varchar(32) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    delivered_at timestamptz,
    last_error text
);
CREATE INDEX idx_notification_pending ON notification_outbox(status, next_attempt_at);

CREATE TABLE metric_series_admissions (
    tenant_id varchar(100) NOT NULL,
    metric_name varchar(300) NOT NULL,
    hour_bucket timestamptz NOT NULL,
    series_hash char(64) NOT NULL,
    PRIMARY KEY (tenant_id, metric_name, hour_bucket, series_hash)
);

CREATE TABLE metric_label_value_admissions (
    tenant_id varchar(100) NOT NULL,
    metric_name varchar(300) NOT NULL,
    hour_bucket timestamptz NOT NULL,
    label_key varchar(300) NOT NULL,
    value_hash char(64) NOT NULL,
    PRIMARY KEY (tenant_id, metric_name, hour_bucket, label_key, value_hash)
);

CREATE TABLE admin_audit_log (
    audit_id uuid PRIMARY KEY,
    tenant_id varchar(100),
    actor varchar(250) NOT NULL,
    action varchar(100) NOT NULL,
    resource_id varchar(250),
    occurred_at timestamptz NOT NULL,
    details_json text NOT NULL
);

INSERT INTO tenants(tenant_id, display_name, status, plan, primary_region,
                    retention_days, created_at, updated_at)
VALUES ('demo', 'Demo Tenant', 'active', 'dev', 'local', 30, now(), now());
