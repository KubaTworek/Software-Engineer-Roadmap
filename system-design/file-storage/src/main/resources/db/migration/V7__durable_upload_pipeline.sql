ALTER TABLE app_users
    ADD COLUMN storage_reserved_bytes BIGINT NOT NULL DEFAULT 0;

ALTER TABLE app_users
    ADD CONSTRAINT chk_storage_counters_non_negative
        CHECK (storage_used_bytes >= 0 AND storage_reserved_bytes >= 0),
    ADD CONSTRAINT chk_storage_quota
        CHECK (storage_used_bytes + storage_reserved_bytes <= storage_quota_bytes);

ALTER TABLE upload_sessions
    ADD COLUMN final_object_key TEXT,
    ADD COLUMN final_file_id UUID REFERENCES file_metadata(id),
    ADD COLUMN assembling_started_at TIMESTAMPTZ,
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX unique_upload_final_file
    ON upload_sessions(final_file_id)
    WHERE final_file_id IS NOT NULL;

ALTER TABLE file_metadata
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;

-- Wiele logicznych plikow/wersji moze wskazywac ten sam kanoniczny blob.
-- Unikalnosc pozostaje na storage_blobs.sha256, a nie na referencjach do obiektu.
ALTER TABLE file_metadata
    DROP CONSTRAINT IF EXISTS file_metadata_object_key_key;

ALTER TABLE file_versions
    DROP CONSTRAINT IF EXISTS file_versions_object_key_key;

ALTER TABLE outbox_events
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX idx_outbox_publishable
    ON outbox_events(status, next_attempt_at, created_at);

ALTER TABLE file_processing_jobs
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN version_id UUID REFERENCES file_versions(id) ON DELETE CASCADE;

UPDATE file_processing_jobs job
SET version_id = file.current_version_id
FROM file_metadata file
WHERE file.id = job.file_id;

ALTER TABLE file_processing_jobs
    ALTER COLUMN version_id SET NOT NULL,
    DROP CONSTRAINT IF EXISTS file_processing_jobs_file_id_job_type_key,
    ADD CONSTRAINT unique_processing_job_per_version UNIQUE(file_id, version_id, job_type);

CREATE INDEX idx_processing_jobs_claim
    ON file_processing_jobs(status, next_attempt_at, created_at);

ALTER TABLE storage_blobs
    ADD CONSTRAINT chk_storage_blob_ref_count CHECK (ref_count >= 0);

CREATE TABLE storage_consistency_findings (
    id UUID PRIMARY KEY,
    finding_type VARCHAR(64) NOT NULL,
    object_key TEXT NOT NULL,
    file_id UUID,
    details TEXT NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_storage_findings_unresolved
    ON storage_consistency_findings(detected_at)
    WHERE resolved_at IS NULL;

-- Changelog ma osobna audience, bo owner zasobu nie jest jedynym klientem sync.
CREATE TABLE change_log_audience (
    change_id BIGINT NOT NULL REFERENCES change_log(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    PRIMARY KEY(change_id, user_id)
);

INSERT INTO change_log_audience(change_id, user_id)
SELECT id, owner_id FROM change_log;

CREATE INDEX idx_change_log_audience_cursor
    ON change_log_audience(user_id, change_id);
