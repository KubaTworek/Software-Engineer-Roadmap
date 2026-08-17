CREATE TABLE IF NOT EXISTS user_features (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    topic_affinity_json TEXT NOT NULL DEFAULT '{}',
    avg_session_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    author_affinity_json TEXT NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS post_features (
    post_id UUID PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE,
    quality_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    spam_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    ctr_1h DOUBLE PRECISION NOT NULL DEFAULT 0,
    ctr_24h DOUBLE PRECISION NOT NULL DEFAULT 0,
    report_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS embeddings (
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    vector TEXT NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(entity_type, entity_id, model_version)
);

CREATE TABLE IF NOT EXISTS experiments (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    traffic_percentage INT NOT NULL,
    variants_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS experiment_assignments (
    experiment_name VARCHAR(120) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    variant VARCHAR(80) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(experiment_name, user_id)
);

CREATE TABLE IF NOT EXISTS moderation_reviews (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    automated_score DOUBLE PRECISION NOT NULL,
    reason TEXT,
    reviewer_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_moderation_reviews_status ON moderation_reviews(status, created_at DESC);

CREATE TABLE IF NOT EXISTS abuse_signals (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    signal_type VARCHAR(80) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_abuse_signals_user ON abuse_signals(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS media_assets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    object_key TEXT NOT NULL,
    media_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    public_url TEXT,
    width INT,
    height INT,
    duration_seconds INT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS sponsored_campaigns (
    id UUID PRIMARY KEY,
    sponsor_name VARCHAR(160) NOT NULL,
    status VARCHAR(50) NOT NULL,
    target_topics TEXT,
    max_impressions BIGINT NOT NULL DEFAULT 0,
    current_impressions BIGINT NOT NULL DEFAULT 0,
    bid_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    creative_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS region_replication_log (
    id UUID PRIMARY KEY,
    region VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    operation VARCHAR(80) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
