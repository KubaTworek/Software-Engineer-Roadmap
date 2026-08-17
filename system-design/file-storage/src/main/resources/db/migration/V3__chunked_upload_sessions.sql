CREATE TABLE upload_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    parent_folder_id UUID REFERENCES folders(id),
    filename TEXT NOT NULL,
    content_type TEXT NOT NULL,
    total_size_bytes BIGINT NOT NULL,
    chunk_size_bytes BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    uploaded_chunks INT NOT NULL DEFAULT 0,
    expected_sha256 TEXT,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE upload_chunks (
    upload_session_id UUID NOT NULL REFERENCES upload_sessions(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    object_key TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(upload_session_id, chunk_index)
);

CREATE INDEX idx_upload_sessions_user_status
ON upload_sessions(user_id, status, created_at DESC);

CREATE INDEX idx_upload_sessions_cleanup
ON upload_sessions(status, expires_at);

CREATE INDEX idx_upload_chunks_session_index
ON upload_chunks(upload_session_id, chunk_index);
