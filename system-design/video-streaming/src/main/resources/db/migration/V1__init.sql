CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE videos (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL,
    visibility TEXT NOT NULL,
    duration_seconds INT,
    owner_id UUID REFERENCES users(id),
    source_object_key TEXT,
    hls_master_object_key TEXT,
    thumbnail_object_key TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_videos_status ON videos(status);
CREATE INDEX idx_videos_published_at ON videos(published_at DESC);

CREATE TABLE uploads (
    id UUID PRIMARY KEY,
    video_id UUID NOT NULL REFERENCES videos(id),
    object_key TEXT NOT NULL,
    filename TEXT NOT NULL,
    content_type TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE TABLE transcoding_jobs (
    id UUID PRIMARY KEY,
    video_id UUID NOT NULL REFERENCES videos(id),
    source_object_key TEXT NOT NULL,
    status TEXT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_transcoding_jobs_status_created ON transcoding_jobs(status, created_at);

CREATE TABLE watch_progress (
    user_id UUID NOT NULL REFERENCES users(id),
    video_id UUID NOT NULL REFERENCES videos(id),
    position_seconds INT NOT NULL,
    duration_seconds INT,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, video_id)
);
