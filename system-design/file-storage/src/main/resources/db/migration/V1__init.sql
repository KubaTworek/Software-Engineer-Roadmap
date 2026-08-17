CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE file_metadata (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id),
    original_filename TEXT NOT NULL,
    content_type TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    sha256 TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_file_metadata_owner_active
ON file_metadata(owner_id, created_at DESC)
WHERE deleted_at IS NULL;

CREATE INDEX idx_file_metadata_owner_deleted
ON file_metadata(owner_id, deleted_at)
WHERE deleted_at IS NOT NULL;
