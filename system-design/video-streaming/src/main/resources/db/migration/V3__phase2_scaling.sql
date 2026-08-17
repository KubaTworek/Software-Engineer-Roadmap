CREATE TABLE qoe_events (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    video_id UUID NOT NULL REFERENCES videos(id),
    session_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    startup_time_ms INT,
    rebuffer_time_ms INT,
    bitrate_kbps INT,
    cdn_provider TEXT,
    player TEXT,
    device_type TEXT,
    country TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_qoe_video_occurred ON qoe_events(video_id, occurred_at DESC);
CREATE INDEX idx_qoe_event_type ON qoe_events(event_type);
CREATE INDEX idx_qoe_session ON qoe_events(session_id);
CREATE INDEX idx_transcoding_jobs_status_attempts ON transcoding_jobs(status, attempts);
