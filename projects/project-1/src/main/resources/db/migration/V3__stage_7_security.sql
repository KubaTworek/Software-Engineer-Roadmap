ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash
    ON refresh_tokens(token_hash);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);
