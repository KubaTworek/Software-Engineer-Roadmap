CREATE SEQUENCE IF NOT EXISTS enterprise_api_key_id_seq START WITH 1000 INCREMENT BY 10;

CREATE TABLE enterprise_api_keys (
    id BIGINT DEFAULT nextval('enterprise_api_key_id_seq') PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    key_hash VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    tier VARCHAR(64) NOT NULL,
    rate_limit_per_minute INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_enterprise_api_keys_key_hash ON enterprise_api_keys(key_hash);
CREATE INDEX idx_enterprise_api_keys_status ON enterprise_api_keys(status);

ALTER TABLE urls ADD COLUMN IF NOT EXISTS home_region VARCHAR(64);
ALTER TABLE urls ADD COLUMN IF NOT EXISTS replicated_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_urls_home_region ON urls(home_region);
