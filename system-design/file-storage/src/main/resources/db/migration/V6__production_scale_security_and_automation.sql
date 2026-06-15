CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);

CREATE TABLE dead_letter_events (
    id UUID PRIMARY KEY,
    source_event_id UUID,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload TEXT NOT NULL,
    failure_reason TEXT NOT NULL,
    failed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dlq_failed_at ON dead_letter_events(failed_at DESC);

CREATE TABLE file_processing_jobs (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL REFERENCES file_metadata(id) ON DELETE CASCADE,
    job_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    result_object_key TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(file_id, job_type)
);

CREATE INDEX idx_file_processing_jobs_status ON file_processing_jobs(status, created_at);

CREATE TABLE storage_blobs (
    id UUID PRIMARY KEY,
    sha256 VARCHAR(64) NOT NULL UNIQUE,
    object_key TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    ref_count BIGINT NOT NULL DEFAULT 1,
    encryption_key_id VARCHAR(200),
    storage_class VARCHAR(64) NOT NULL DEFAULT 'STANDARD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_storage_blobs_sha256 ON storage_blobs(sha256);

ALTER TABLE file_versions ADD COLUMN IF NOT EXISTS blob_id UUID REFERENCES storage_blobs(id);
ALTER TABLE file_metadata ADD COLUMN IF NOT EXISTS thumbnail_object_key TEXT;
ALTER TABLE file_metadata ADD COLUMN IF NOT EXISTS encryption_key_id VARCHAR(200);
ALTER TABLE file_metadata ADD COLUMN IF NOT EXISTS storage_class VARCHAR(64) NOT NULL DEFAULT 'STANDARD';

CREATE TABLE backup_runs (
    id UUID PRIMARY KEY,
    backup_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    location TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    details TEXT
);

CREATE INDEX idx_backup_runs_started_at ON backup_runs(started_at DESC);

CREATE TABLE storage_cost_snapshots (
    id UUID PRIMARY KEY,
    total_objects BIGINT NOT NULL,
    total_logical_bytes BIGINT NOT NULL,
    total_blob_bytes BIGINT NOT NULL,
    estimated_monthly_cost_usd NUMERIC(12, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
