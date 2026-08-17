CREATE TABLE IF NOT EXISTS processed_kafka_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS dead_letter_events (
    id UUID PRIMARY KEY,
    event_id UUID,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    error_message TEXT NOT NULL,
    attempts INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dead_letter_events_created_at ON dead_letter_events(created_at DESC);

CREATE TABLE IF NOT EXISTS follower_shards (
    followee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shard_id INT NOT NULL,
    follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (followee_id, shard_id, follower_id)
);

CREATE INDEX IF NOT EXISTS idx_follower_shards_follower_id ON follower_shards(follower_id);

CREATE TABLE IF NOT EXISTS celebrity_authors (
    author_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    follower_count BIGINT NOT NULL DEFAULT 0,
    celebrity_since TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS celebrity_posts (
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (author_id, post_id)
);

CREATE INDEX IF NOT EXISTS idx_celebrity_posts_author_created_at
    ON celebrity_posts(author_id, created_at DESC);

CREATE TABLE IF NOT EXISTS counter_shards (
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    counter_name VARCHAR(50) NOT NULL,
    shard_id INT NOT NULL,
    value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (entity_type, entity_id, counter_name, shard_id)
);

CREATE INDEX IF NOT EXISTS idx_counter_shards_entity
    ON counter_shards(entity_type, entity_id, counter_name);

ALTER TABLE feed_inbox
    ADD COLUMN IF NOT EXISTS source VARCHAR(50) NOT NULL DEFAULT 'FOLLOWING',
    ADD COLUMN IF NOT EXISTS shard_id INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_feed_inbox_user_created
    ON feed_inbox(user_id, created_at DESC, post_id DESC);

CREATE INDEX IF NOT EXISTS idx_feed_inbox_shard
    ON feed_inbox(shard_id, created_at DESC);
