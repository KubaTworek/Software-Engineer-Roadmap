ALTER TABLE app_users
    ADD COLUMN storage_quota_bytes BIGINT NOT NULL DEFAULT 1073741824,
    ADD COLUMN storage_used_bytes BIGINT NOT NULL DEFAULT 0;

CREATE TABLE folders (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id),
    parent_folder_id UUID REFERENCES folders(id),
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

ALTER TABLE file_metadata
    ADD COLUMN parent_folder_id UUID REFERENCES folders(id),
    ADD COLUMN name TEXT;

UPDATE file_metadata SET name = original_filename WHERE name IS NULL;
ALTER TABLE file_metadata ALTER COLUMN name SET NOT NULL;

UPDATE app_users u
SET storage_used_bytes = COALESCE((
    SELECT SUM(f.size_bytes)
    FROM file_metadata f
    WHERE f.owner_id = u.id
), 0);

CREATE UNIQUE INDEX unique_active_folder_name_in_parent
ON folders(owner_id, parent_folder_id, lower(name))
WHERE deleted_at IS NULL AND parent_folder_id IS NOT NULL;

CREATE UNIQUE INDEX unique_active_folder_name_in_root
ON folders(owner_id, lower(name))
WHERE deleted_at IS NULL AND parent_folder_id IS NULL;

CREATE UNIQUE INDEX unique_active_file_name_in_parent
ON file_metadata(owner_id, parent_folder_id, lower(name))
WHERE deleted_at IS NULL AND parent_folder_id IS NOT NULL;

CREATE UNIQUE INDEX unique_active_file_name_in_root
ON file_metadata(owner_id, lower(name))
WHERE deleted_at IS NULL AND parent_folder_id IS NULL;

CREATE INDEX idx_folders_owner_parent_active
ON folders(owner_id, parent_folder_id, name)
WHERE deleted_at IS NULL;

CREATE INDEX idx_folders_owner_deleted
ON folders(owner_id, deleted_at)
WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_file_metadata_owner_parent_active
ON file_metadata(owner_id, parent_folder_id, name)
WHERE deleted_at IS NULL;
