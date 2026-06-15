ALTER TABLE file_metadata
    ADD COLUMN current_version_id UUID,
    ADD COLUMN current_version_number INTEGER NOT NULL DEFAULT 1;

CREATE TABLE file_versions (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL REFERENCES file_metadata(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    content_type TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 TEXT NOT NULL,
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL,
    conflict_version BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(file_id, version_number)
);

WITH inserted_versions AS (
    INSERT INTO file_versions (
        id, file_id, version_number, object_key, content_type, size_bytes, sha256, created_by, created_at, conflict_version
    )
    SELECT uuid_generate_v4(), id, 1, object_key, content_type, size_bytes, sha256, owner_id, created_at, FALSE
    FROM file_metadata
    RETURNING id, file_id
)
UPDATE file_metadata f
SET current_version_id = v.id,
    current_version_number = 1
FROM inserted_versions v
WHERE f.id = v.file_id;

CREATE INDEX idx_file_versions_file_number
ON file_versions(file_id, version_number DESC);

CREATE TABLE change_log (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES app_users(id),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    resource_type TEXT NOT NULL CHECK (resource_type IN ('FILE', 'FOLDER')),
    resource_id UUID NOT NULL,
    operation TEXT NOT NULL,
    payload TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_change_log_owner_cursor
ON change_log(owner_id, id);

CREATE INDEX idx_change_log_resource
ON change_log(resource_type, resource_id, id DESC);

CREATE TABLE search_index (
    id UUID PRIMARY KEY,
    resource_type TEXT NOT NULL CHECK (resource_type IN ('FILE', 'FOLDER')),
    resource_id UUID NOT NULL,
    owner_id UUID NOT NULL REFERENCES app_users(id),
    name TEXT NOT NULL,
    content_type TEXT,
    size_bytes BIGINT,
    searchable_text TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX unique_search_index_resource
ON search_index(resource_type, resource_id);

CREATE INDEX idx_search_index_owner_text
ON search_index(owner_id, searchable_text);

INSERT INTO search_index (id, resource_type, resource_id, owner_id, name, content_type, size_bytes, searchable_text, updated_at)
SELECT id, 'FILE', id, owner_id, name, content_type, size_bytes, lower(name || ' ' || content_type), updated_at
FROM file_metadata
WHERE deleted_at IS NULL;

INSERT INTO search_index (id, resource_type, resource_id, owner_id, name, content_type, size_bytes, searchable_text, updated_at)
SELECT id, 'FOLDER', id, owner_id, name, 'folder', NULL, lower(name || ' folder'), updated_at
FROM folders
WHERE deleted_at IS NULL;
