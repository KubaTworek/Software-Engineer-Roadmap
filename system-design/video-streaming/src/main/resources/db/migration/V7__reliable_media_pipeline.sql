ALTER TABLE transcoding_jobs ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE transcoding_jobs ADD COLUMN lease_token BIGINT NOT NULL DEFAULT 0;
ALTER TABLE transcoding_jobs ADD COLUMN lease_until TIMESTAMPTZ;
ALTER TABLE videos ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE live_streams ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE live_streams ADD COLUMN owner_token BIGINT NOT NULL DEFAULT 0;
ALTER TABLE live_streams ADD COLUMN lease_until TIMESTAMPTZ;
ALTER TABLE transcoding_jobs ADD CONSTRAINT uk_transcoding_source UNIQUE (source_object_key);

CREATE TABLE transcoding_outbox (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES transcoding_jobs(id),
    video_id UUID NOT NULL REFERENCES videos(id),
    source_object_key TEXT NOT NULL,
    attempt INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    delivery_attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    CONSTRAINT uk_transcoding_outbox_job UNIQUE (job_id)
);

CREATE INDEX idx_transcoding_outbox_pending
    ON transcoding_outbox(created_at)
    WHERE published_at IS NULL;
