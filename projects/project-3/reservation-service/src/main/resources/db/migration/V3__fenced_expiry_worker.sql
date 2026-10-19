ALTER TABLE event_inventory ADD COLUMN last_fencing_token BIGINT NOT NULL DEFAULT 0;

CREATE TABLE worker_leases (
    lease_name VARCHAR(100) PRIMARY KEY,
    owner_id VARCHAR(200) NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE NOT NULL,
    fencing_token BIGINT NOT NULL
);
