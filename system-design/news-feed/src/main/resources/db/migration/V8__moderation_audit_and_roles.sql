ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(30) NOT NULL DEFAULT 'USER';

ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'MODERATOR', 'ADMIN'));

CREATE TABLE moderation_audit_log (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES moderation_reviews(id),
    entity_id UUID NOT NULL,
    reviewer_id UUID NOT NULL REFERENCES users(id),
    decision VARCHAR(30) NOT NULL CHECK (decision IN ('approved', 'rejected')),
    reason TEXT,
    decided_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_moderation_audit_entity ON moderation_audit_log(entity_id, decided_at DESC);
