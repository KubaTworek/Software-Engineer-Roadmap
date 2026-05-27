-- =========================================================
-- REPLICATION, STALE READS AND SHARDING HOTSPOTS
-- PostgreSQL practical demo
-- =========================================================
-- Goal:
-- Demonstrate:
-- - read-after-write consistency problem
-- - leader vs replica read model
-- - async replication lag simulation
-- - stale reads
-- - sharding key choices
-- - hotspot caused by increasing shard keys
-- - better distribution using hash-based sharding
--
-- NOTE:
-- This file does not configure real PostgreSQL replication.
-- Instead, it simulates leader/replica behavior using tables.
--
-- Real production setup:
-- - leader / primary receives writes
-- - replica receives async copied data
-- - reads from replica may be stale
-- =========================================================



-- =========================================================
-- 0. CLEAN SETUP
-- =========================================================

DROP TABLE IF EXISTS leader_orders;
DROP TABLE IF EXISTS replica_orders;

DROP TABLE IF EXISTS events_bad_sharding;
DROP TABLE IF EXISTS events_hash_sharding;

DROP TABLE IF EXISTS shard_stats;
DROP TABLE IF EXISTS tenants;



-- =========================================================
-- 1. LEADER / REPLICA SIMULATION
-- =========================================================
-- leader_orders simulates primary database.
-- replica_orders simulates asynchronous read replica.
--
-- In real systems, writes go to leader.
-- Reads may go to replica.
--
-- If replication is async, replica may lag behind.
-- =========================================================

CREATE TABLE leader_orders (
                               id BIGSERIAL PRIMARY KEY,
                               user_id BIGINT NOT NULL,
                               status TEXT NOT NULL,
                               total_amount NUMERIC(12,2) NOT NULL,
                               version BIGINT NOT NULL DEFAULT 1,
                               created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                               updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);



CREATE TABLE replica_orders (
                                id BIGINT PRIMARY KEY,
                                user_id BIGINT NOT NULL,
                                status TEXT NOT NULL,
                                total_amount NUMERIC(12,2) NOT NULL,
                                version BIGINT NOT NULL,
                                created_at TIMESTAMP NOT NULL,
                                updated_at TIMESTAMP NOT NULL,
                                replicated_at TIMESTAMP NOT NULL DEFAULT NOW()
);



-- =========================================================
-- 2. WRITE TO LEADER
-- =========================================================
-- Application writes new order to leader.
-- =========================================================

INSERT INTO leader_orders (
    user_id,
    status,
    total_amount
)
VALUES (
           1,
           'PAID',
           199.99
       )
    RETURNING *;



-- =========================================================
-- 3. IMMEDIATE READ FROM REPLICA
-- =========================================================
-- This simulates stale read.
--
-- The row exists on leader,
-- but replica has not received it yet.
--
-- Result:
-- no rows returned.
--
-- This means:
-- no read-after-write consistency.
-- =========================================================

SELECT *
FROM replica_orders
WHERE user_id = 1
ORDER BY id DESC;



-- =========================================================
-- 4. READ FROM LEADER AFTER WRITE
-- =========================================================
-- Correct approach when application must immediately
-- see its own writes.
-- =========================================================

SELECT *
FROM leader_orders
WHERE user_id = 1
ORDER BY id DESC;



-- =========================================================
-- 5. SIMULATE ASYNC REPLICATION
-- =========================================================
-- Replica catches up later.
-- In real PostgreSQL this would be WAL replication.
-- Here we manually copy missing rows.
-- =========================================================

INSERT INTO replica_orders (
    id,
    user_id,
    status,
    total_amount,
    version,
    created_at,
    updated_at,
    replicated_at
)
SELECT
    id,
    user_id,
    status,
    total_amount,
    version,
    created_at,
    updated_at,
    NOW()
FROM leader_orders lo
WHERE NOT EXISTS (
    SELECT 1
    FROM replica_orders ro
    WHERE ro.id = lo.id
);



-- =========================================================
-- 6. READ FROM REPLICA AFTER CATCH-UP
-- =========================================================

SELECT *
FROM replica_orders
WHERE user_id = 1
ORDER BY id DESC;



-- =========================================================
-- 7. UPDATE ON LEADER, STALE READ ON REPLICA
-- =========================================================
-- Update order on leader.
-- Replica still has old version.
-- =========================================================

UPDATE leader_orders
SET
    status = 'REFUNDED',
    version = version + 1,
    updated_at = NOW()
WHERE id = 1
    RETURNING *;



-- Replica still shows old status/version.

SELECT *
FROM replica_orders
WHERE id = 1;



-- Leader shows current status/version.

SELECT *
FROM leader_orders
WHERE id = 1;



-- =========================================================
-- 8. DETECTING STALE READS USING VERSION
-- =========================================================
-- Application expects at least version = 2,
-- because it just performed an update.
--
-- Replica still has version = 1.
--
-- Application can detect stale data and:
-- - retry later
-- - wait
-- - read from leader
-- =========================================================

SELECT
    id,
    status,
    version,
    CASE
        WHEN version < 2 THEN 'STALE_READ'
        ELSE 'FRESH_ENOUGH'
        END AS read_status
FROM replica_orders
WHERE id = 1;



-- =========================================================
-- 9. FALLBACK TO LEADER IF REPLICA IS STALE
-- =========================================================
-- In real application code:
--
-- if replica.version < expected_version:
--     read_from_leader()
--
-- SQL demonstration:
-- =========================================================

SELECT *
FROM leader_orders
WHERE id = 1;



-- =========================================================
-- 10. REPLICA CATCHES UP AGAIN
-- =========================================================

UPDATE replica_orders ro
SET
    status = lo.status,
    total_amount = lo.total_amount,
    version = lo.version,
    updated_at = lo.updated_at,
    replicated_at = NOW()
    FROM leader_orders lo
WHERE ro.id = lo.id;



SELECT *
FROM replica_orders
WHERE id = 1;



-- =========================================================
-- 11. READ ROUTING STRATEGY
-- =========================================================
-- Recommended application-level logic:
--
-- After write:
-- - read from leader for a short consistency window
-- - or use sticky session
-- - or require replica version >= expected version
--
-- For non-critical reads:
-- - read from replica
--
-- For strong read-after-write consistency:
-- - read from leader
-- - or use synchronous replication
-- =========================================================



-- =========================================================
-- 12. SHARDING SETUP
-- =========================================================
-- Sharding splits data across multiple physical/logical shards.
--
-- Bad shard key:
-- - increasing timestamp
-- - auto-increment ID
-- - created_at range
--
-- Good shard key:
-- - hash(user_id)
-- - hash(tenant_id)
-- - compound key
--
-- =========================================================



-- =========================================================
-- 13. BAD SHARDING BY CREATED_AT RANGE
-- =========================================================
-- This simulates range-based sharding by month.
--
-- Problem:
-- all new writes go to the newest shard.
--
-- Older shards become cold.
-- Newest shard becomes hotspot.
-- =========================================================

CREATE TABLE events_bad_sharding (
                                     id BIGSERIAL PRIMARY KEY,
                                     user_id BIGINT NOT NULL,
                                     event_type TEXT NOT NULL,
                                     created_at TIMESTAMP NOT NULL,
                                     shard_name TEXT NOT NULL
);



-- =========================================================
-- 14. INSERT HISTORICAL DATA
-- =========================================================

INSERT INTO events_bad_sharding (
    user_id,
    event_type,
    created_at,
    shard_name
)
SELECT
    (random() * 100000)::BIGINT,
    'page_view',
    '2025-11-01'::timestamp + ((random() * 30) || ' days')::INTERVAL,
    'shard_2025_11'
FROM generate_series(1, 10000);



INSERT INTO events_bad_sharding (
    user_id,
    event_type,
    created_at,
    shard_name
)
SELECT
    (random() * 100000)::BIGINT,
    'page_view',
    '2025-12-01'::timestamp + ((random() * 30) || ' days')::INTERVAL,
    'shard_2025_12'
FROM generate_series(1, 10000);



-- =========================================================
-- 15. INSERT CURRENT HOT DATA
-- =========================================================
-- All new writes go to the newest time range shard.
-- This creates hotspot.
-- =========================================================

INSERT INTO events_bad_sharding (
    user_id,
    event_type,
    created_at,
    shard_name
)
SELECT
    (random() * 100000)::BIGINT,
    'page_view',
    '2026-01-01'::timestamp + ((random() * 30) || ' days')::INTERVAL,
    'shard_2026_01'
FROM generate_series(1, 100000);



-- =========================================================
-- 16. SHOW HOTSPOT
-- =========================================================

SELECT
    shard_name,
    COUNT(*) AS rows_per_shard,
    ROUND(
            COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (),
            2
    ) AS percentage_of_total
FROM events_bad_sharding
GROUP BY shard_name
ORDER BY rows_per_shard DESC;



-- Expected:
-- shard_2026_01 has most writes.
--
-- In production:
-- this shard receives almost all current inserts.
-- It becomes bottleneck.
-- =========================================================



-- =========================================================
-- 17. BETTER SHARDING BY HASH(USER_ID)
-- =========================================================
-- Hashing spreads writes across many shards.
--
-- Instead of routing by time:
-- shard = hash(user_id) % number_of_shards
--
-- This avoids all new writes going to one shard.
-- =========================================================

CREATE TABLE events_hash_sharding (
                                      id BIGSERIAL PRIMARY KEY,
                                      user_id BIGINT NOT NULL,
                                      event_type TEXT NOT NULL,
                                      created_at TIMESTAMP NOT NULL,
                                      shard_id INTEGER NOT NULL
);



-- =========================================================
-- 18. INSERT DATA USING HASH-BASED SHARDING
-- =========================================================
-- PostgreSQL modulo operation:
-- user_id % 8
--
-- In real systems use stable hash function,
-- not necessarily raw modulo.
-- =========================================================

INSERT INTO events_hash_sharding (
    user_id,
    event_type,
    created_at,
    shard_id
)
SELECT
    user_id,
    'page_view',
    NOW() - ((random() * 3600) || ' seconds')::INTERVAL,
    (user_id % 8)::INTEGER AS shard_id
FROM (
         SELECT
             (random() * 1000000)::BIGINT AS user_id
         FROM generate_series(1, 120000)
     ) x;



-- =========================================================
-- 19. SHOW EVEN DISTRIBUTION
-- =========================================================

SELECT
    shard_id,
    COUNT(*) AS rows_per_shard,
    ROUND(
            COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (),
            2
    ) AS percentage_of_total
FROM events_hash_sharding
GROUP BY shard_id
ORDER BY shard_id;



-- Expected:
-- rows are distributed more evenly across shards.
-- =========================================================



-- =========================================================
-- 20. COMPOUND SHARD KEY
-- =========================================================
-- Compound key idea:
-- - distribute writes by hash bucket
-- - keep created_at for local ordering/range scans
--
-- Example logical shard key:
-- (user_id % 16, created_at)
--
-- Benefits:
-- - avoids timestamp hotspot
-- - still allows time-based queries inside bucket
-- =========================================================

SELECT
    (user_id % 16) AS hash_bucket,
    DATE_TRUNC('hour', created_at) AS hour_bucket,
    COUNT(*) AS events_count
FROM events_hash_sharding
GROUP BY
    hash_bucket,
    hour_bucket
ORDER BY
    hour_bucket DESC,
    hash_bucket;



-- =========================================================
-- 21. HOT TENANT PROBLEM
-- =========================================================
-- Even hash sharding can have hotspots
-- if one tenant/user produces huge traffic.
--
-- Example:
-- tenant_id = 999 generates 50% of writes.
-- =========================================================

CREATE TABLE tenants (
                         tenant_id BIGINT PRIMARY KEY,
                         tenant_name TEXT NOT NULL,
                         dedicated_shard BOOLEAN NOT NULL DEFAULT FALSE
);



INSERT INTO tenants (
    tenant_id,
    tenant_name,
    dedicated_shard
)
VALUES
    (1, 'Small Tenant A', FALSE),
    (2, 'Small Tenant B', FALSE),
    (999, 'Huge Tenant', FALSE);



-- Add normal traffic.

INSERT INTO events_hash_sharding (
    user_id,
    event_type,
    created_at,
    shard_id
)
SELECT
    (random() * 100000)::BIGINT,
    'normal_event',
    NOW(),
    ((random() * 100000)::BIGINT % 8)::INTEGER
FROM generate_series(1, 10000);



-- Add hot tenant traffic.
-- Here user_id = 999 always maps to the same shard.

INSERT INTO events_hash_sharding (
    user_id,
    event_type,
    created_at,
    shard_id
)
SELECT
    999,
    'hot_tenant_event',
    NOW(),
    (999 % 8)::INTEGER
FROM generate_series(1, 50000);



-- =========================================================
-- 22. DETECT HOT SHARDS
-- =========================================================

SELECT
    shard_id,
    COUNT(*) AS events_count,
    ROUND(
            COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (),
            2
    ) AS percentage_of_total
FROM events_hash_sharding
GROUP BY shard_id
ORDER BY events_count DESC;



-- =========================================================
-- 23. DETECT HOT KEYS / HOT TENANTS
-- =========================================================

SELECT
    user_id,
    COUNT(*) AS events_count,
    ROUND(
            COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (),
            2
    ) AS percentage_of_total
FROM events_hash_sharding
GROUP BY user_id
ORDER BY events_count DESC
    LIMIT 10;



-- =========================================================
-- 24. MITIGATION: DEDICATED SHARD FOR HOT TENANT
-- =========================================================
-- If one tenant generates huge traffic,
-- move it to a dedicated shard.
--
-- This is often called:
-- - hot tenant isolation
-- - tenant splitting
-- - dedicated partition
-- =========================================================

UPDATE tenants
SET dedicated_shard = TRUE
WHERE tenant_id = 999;



SELECT *
FROM tenants
ORDER BY tenant_id;



-- =========================================================
-- 25. ROUTING FUNCTION SIMULATION
-- =========================================================
-- This CASE expression simulates application routing.
--
-- If tenant is dedicated:
-- - route to special shard 100
--
-- Otherwise:
-- - route by hash/modulo
-- =========================================================

SELECT
    e.user_id,
    CASE
        WHEN t.dedicated_shard = TRUE THEN 100
        ELSE (e.user_id % 8)::INTEGER
END AS routed_shard
FROM events_hash_sharding e
LEFT JOIN tenants t
    ON t.tenant_id = e.user_id
WHERE e.user_id IN (1, 2, 999)
LIMIT 50;



-- =========================================================
-- 26. IMPORTANT SHARDING TRADE-OFFS
-- =========================================================
-- Sharding improves write scalability,
-- but introduces serious complexity:
--
-- - no easy global JOINs
-- - distributed transactions are hard
-- - resharding is expensive
-- - query routing becomes application concern
-- - global uniqueness requires design
-- - global ORDER BY is harder
-- - cross-shard analytics require aggregation layer
--
-- Do not shard too early.
-- =========================================================



-- =========================================================
-- 27. FINAL CHECKS
-- =========================================================

SELECT COUNT(*) AS leader_rows
FROM leader_orders;

SELECT COUNT(*) AS replica_rows
FROM replica_orders;

SELECT COUNT(*) AS bad_sharding_rows
FROM events_bad_sharding;

SELECT COUNT(*) AS hash_sharding_rows
FROM events_hash_sharding;