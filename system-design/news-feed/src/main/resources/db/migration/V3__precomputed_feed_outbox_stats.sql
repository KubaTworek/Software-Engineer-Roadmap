CREATE TABLE domain_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_domain_events_processing
    ON domain_events(status, next_attempt_at, created_at);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY REFERENCES domain_events(id) ON DELETE CASCADE,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE feed_inbox (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(50) NOT NULL,
    score DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, post_id)
);

CREATE INDEX idx_feed_inbox_user_created
    ON feed_inbox(user_id, created_at DESC, post_id DESC);

CREATE INDEX idx_feed_inbox_post_id
    ON feed_inbox(post_id);

CREATE TABLE post_stats (
    post_id UUID PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE,
    like_count BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);
