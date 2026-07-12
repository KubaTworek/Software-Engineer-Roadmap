CREATE TABLE personalization_events (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    event_type TEXT NOT NULL,
    video_id UUID REFERENCES videos(id),
    session_id TEXT,
    source TEXT,
    device_type TEXT,
    country TEXT,
    attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_personalization_events_user_time ON personalization_events(user_id, occurred_at DESC);
CREATE INDEX idx_personalization_events_video_time ON personalization_events(video_id, occurred_at DESC);
CREATE INDEX idx_personalization_events_type_time ON personalization_events(event_type, occurred_at DESC);

CREATE TABLE warehouse_daily_video_metrics (
    metric_date DATE NOT NULL,
    video_id UUID NOT NULL REFERENCES videos(id),
    views BIGINT NOT NULL DEFAULT 0,
    starts BIGINT NOT NULL DEFAULT 0,
    completions BIGINT NOT NULL DEFAULT 0,
    unique_users BIGINT NOT NULL DEFAULT 0,
    avg_startup_ms NUMERIC(12,2) NOT NULL DEFAULT 0,
    rebuffer_ratio NUMERIC(12,4) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (metric_date, video_id)
);
CREATE INDEX idx_warehouse_daily_video_metrics_views ON warehouse_daily_video_metrics(metric_date DESC, views DESC);

CREATE TABLE feature_store_user (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    favorite_category TEXT NOT NULL DEFAULT 'general',
    watched_videos_30d INT NOT NULL DEFAULT 0,
    completed_videos_30d INT NOT NULL DEFAULT 0,
    avg_completion_rate NUMERIC(8,4) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE feature_store_video (
    video_id UUID PRIMARY KEY REFERENCES videos(id),
    views_7d BIGINT NOT NULL DEFAULT 0,
    views_30d BIGINT NOT NULL DEFAULT 0,
    completion_rate_7d NUMERIC(8,4) NOT NULL DEFAULT 0,
    quality_score_7d NUMERIC(10,4) NOT NULL DEFAULT 0,
    trending_score NUMERIC(14,4) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_feature_store_video_trending_score ON feature_store_video(trending_score DESC);

CREATE TABLE recommendation_candidates (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    video_id UUID NOT NULL REFERENCES videos(id),
    algorithm TEXT NOT NULL,
    reason TEXT NOT NULL,
    score NUMERIC(14,4) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, video_id, algorithm)
);
CREATE INDEX idx_recommendation_candidates_user_score ON recommendation_candidates(user_id, score DESC);

CREATE TABLE ab_experiments (
    id UUID PRIMARY KEY,
    experiment_key TEXT NOT NULL UNIQUE,
    description TEXT,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE ab_experiment_variants (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES ab_experiments(id) ON DELETE CASCADE,
    variant_key TEXT NOT NULL,
    traffic_percent INT NOT NULL,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (experiment_id, variant_key)
);

CREATE TABLE ab_assignments (
    id UUID PRIMARY KEY,
    experiment_key TEXT NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    variant_key TEXT NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    UNIQUE (experiment_key, user_id)
);

INSERT INTO ab_experiments (id, experiment_key, description, status, created_at)
VALUES (uuid_generate_v4(), 'home_recommendations_ranking', 'Compares baseline hybrid ranking with trending and freshness boosted rankings.', 'RUNNING', now())
ON CONFLICT (experiment_key) DO NOTHING;

INSERT INTO ab_experiment_variants (id, experiment_id, variant_key, traffic_percent, config_json)
SELECT uuid_generate_v4(), e.id, v.variant_key, v.traffic_percent, v.config_json::jsonb
FROM ab_experiments e
CROSS JOIN (VALUES
    ('control', 50, '{"algorithm":"phase4_hybrid_v1"}'),
    ('trending_boost', 30, '{"algorithm":"phase4_trending_boost_v1"}'),
    ('freshness_boost', 20, '{"algorithm":"phase4_freshness_boost_v1"}')
) AS v(variant_key, traffic_percent, config_json)
WHERE e.experiment_key = 'home_recommendations_ranking'
ON CONFLICT DO NOTHING;
