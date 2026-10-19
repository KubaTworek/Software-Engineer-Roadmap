-- V7 domyka granice, których nie może bezpiecznie egzekwować sama projekcja feedu.
-- Istniejące posty po migracji pozostają widoczne, dlatego wartości domyślne
-- odpowiadają zachowaniu systemu sprzed wprowadzenia moderacji i prywatności.
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(30) NOT NULL DEFAULT 'PUBLIC';

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_status
        CHECK (status IN ('PENDING_REVIEW', 'PUBLISHED', 'HIDDEN', 'DELETED')),
    ADD CONSTRAINT chk_posts_visibility
        CHECK (visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE'));

CREATE INDEX IF NOT EXISTS idx_posts_public_feed
    ON posts(created_at DESC, id DESC)
    WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC' AND deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT chk_user_blocks_not_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked
    ON user_blocks(blocked_id, blocker_id);

-- Idempotencja jest własnością konkretnej projekcji. Fan-out i indeks wyszukiwania
-- muszą móc niezależnie obsłużyć to samo event_id dokładnie raz lokalnie.
ALTER TABLE processed_kafka_events
    ADD COLUMN IF NOT EXISTS consumer_name VARCHAR(100) NOT NULL DEFAULT 'legacy';

ALTER TABLE processed_kafka_events
    DROP CONSTRAINT IF EXISTS processed_kafka_events_pkey;

ALTER TABLE processed_kafka_events
    ADD CONSTRAINT processed_kafka_events_pkey PRIMARY KEY (consumer_name, event_id);

ALTER TABLE processed_kafka_events
    ALTER COLUMN consumer_name DROP DEFAULT;
