CREATE TABLE follows (
    follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT chk_follows_not_self CHECK (follower_id <> followee_id)
);
CREATE INDEX idx_follows_followee_id ON follows(followee_id);
CREATE INDEX idx_follows_follower_id ON follows(follower_id);
CREATE TABLE post_likes (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_post_likes_user_id ON post_likes(user_id);
CREATE INDEX idx_post_likes_post_id ON post_likes(post_id);
CREATE TABLE comments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_comments_post_id_created_at ON comments(post_id, created_at ASC) WHERE deleted_at IS NULL;
CREATE INDEX idx_comments_author_id ON comments(author_id);
