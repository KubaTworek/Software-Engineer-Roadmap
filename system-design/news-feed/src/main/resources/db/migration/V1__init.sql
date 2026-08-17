CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    bio VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE auth_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(128) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_tokens_token ON auth_tokens(token);
CREATE INDEX idx_auth_tokens_user_id ON auth_tokens(user_id);

CREATE TABLE posts (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_posts_feed ON posts(created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_author_id ON posts(author_id);
