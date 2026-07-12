CREATE TABLE live_streams (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    owner_id UUID REFERENCES users(id),
    status TEXT NOT NULL,
    latency_mode TEXT NOT NULL,
    stream_key TEXT NOT NULL UNIQUE,
    ingest_url TEXT,
    internal_ingest_url TEXT,
    hls_master_object_key TEXT,
    dvr_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    dvr_window_seconds INT NOT NULL DEFAULT 7200,
    recording_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    recording_object_key TEXT,
    vod_video_id UUID REFERENCES videos(id),
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_live_streams_status_created ON live_streams(status, created_at DESC);
CREATE INDEX idx_live_streams_stream_key ON live_streams(stream_key);
CREATE INDEX idx_live_streams_owner ON live_streams(owner_id);
