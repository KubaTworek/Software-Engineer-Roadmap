CREATE TABLE resource_permissions (
    id UUID PRIMARY KEY,
    resource_type TEXT NOT NULL CHECK (resource_type IN ('FILE', 'FOLDER')),
    resource_id UUID NOT NULL,
    grantee_user_id UUID NOT NULL REFERENCES app_users(id),
    role TEXT NOT NULL CHECK (role IN ('VIEWER', 'EDITOR', 'OWNER')),
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX unique_resource_permission_grantee
ON resource_permissions(resource_type, resource_id, grantee_user_id);

CREATE INDEX idx_resource_permissions_resource
ON resource_permissions(resource_type, resource_id);

CREATE INDEX idx_resource_permissions_grantee_active
ON resource_permissions(grantee_user_id, revoked_at);

CREATE TABLE share_links (
    id UUID PRIMARY KEY,
    resource_type TEXT NOT NULL CHECK (resource_type IN ('FILE', 'FOLDER')),
    resource_id UUID NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL CHECK (role IN ('VIEWER', 'EDITOR', 'OWNER')),
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_share_links_resource
ON share_links(resource_type, resource_id);

CREATE INDEX idx_share_links_active
ON share_links(token_hash, revoked_at, expires_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES app_users(id),
    action TEXT NOT NULL,
    resource_type TEXT CHECK (resource_type IN ('FILE', 'FOLDER')),
    resource_id UUID,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_logs_actor_created
ON audit_logs(actor_user_id, created_at DESC);

CREATE INDEX idx_audit_logs_resource_created
ON audit_logs(resource_type, resource_id, created_at DESC);
