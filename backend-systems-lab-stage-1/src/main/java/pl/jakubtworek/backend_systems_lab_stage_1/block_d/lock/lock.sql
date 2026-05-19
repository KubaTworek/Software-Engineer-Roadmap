-- =========================================================
-- OPTIMISTIC VS PESSIMISTIC LOCKING
-- PostgreSQL practical examples
-- =========================================================
-- Goal:
-- Demonstrate:
-- - pessimistic locking with SELECT ... FOR UPDATE
-- - optimistic locking with version column
-- - contention behavior
-- - retry-based concurrency control
--
-- IMPORTANT:
-- Most examples require two separate sessions:
-- - SESSION 1
-- - SESSION 2
--
-- Execute step-by-step in correct order.
-- =========================================================



-- =========================================================
-- 0. CLEAN SETUP
-- =========================================================

DROP TABLE IF EXISTS accounts;

CREATE TABLE accounts (
                          id BIGINT PRIMARY KEY,
                          owner_name TEXT NOT NULL,
                          balance INTEGER NOT NULL,
                          version INTEGER NOT NULL DEFAULT 1
);



INSERT INTO accounts (
    id,
    owner_name,
    balance,
    version
)
VALUES (
           1,
           'Alice',
           1000,
           1
       );



-- =========================================================
-- 1. INITIAL STATE
-- =========================================================

SELECT *
FROM accounts;



-- =========================================================
-- =========================================================
-- PESSIMISTIC LOCKING
-- =========================================================
-- =========================================================
--
-- Concept:
-- Lock the row immediately.
--
-- Prevent concurrent modifications until transaction ends.
--
-- Mechanism:
-- SELECT ... FOR UPDATE
--
-- Advantages:
-- - strong consistency
-- - simple mental model
-- - no retry logic required
--
-- Disadvantages:
-- - reduced concurrency
-- - waiting on locks
-- - lower throughput under contention
-- - possible deadlocks
--
-- Best when:
-- - conflicts are frequent
-- - correctness is critical
-- - transactions are short
--
-- =========================================================



-- =========================================================
-- 2. PESSIMISTIC LOCKING EXAMPLE
-- =========================================================
-- SESSION 1 acquires row lock.
-- SESSION 2 attempting UPDATE will block.
-- =========================================================



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SELECT *
FROM accounts
WHERE id = 1
    FOR UPDATE;

-- Row is now locked.
--
-- No other transaction can update this row
-- until SESSION 1 commits or rolls back.
--
-- Keep transaction open.
-- Go to SESSION 2.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

UPDATE accounts
SET balance = balance - 200
WHERE id = 1;

-- This UPDATE blocks.
--
-- PostgreSQL waits for SESSION 1
-- to release the row lock.
--
-- Session appears "stuck".
--
-- Go back to SESSION 1.



-- =========================================================
-- SESSION 1
-- =========================================================

UPDATE accounts
SET balance = balance - 100
WHERE id = 1;

COMMIT;

-- Lock released.



-- =========================================================
-- SESSION 2
-- =========================================================
-- UPDATE resumes automatically after lock release.
-- =========================================================

COMMIT;



-- =========================================================
-- CHECK FINAL STATE
-- =========================================================
-- Final balance:
-- 1000 - 100 - 200 = 700
-- =========================================================

SELECT *
FROM accounts;



-- =========================================================
-- 3. LOCK WAIT OBSERVATION
-- =========================================================
-- Useful query for inspecting blocking sessions.
-- =========================================================

SELECT
    pid,
    wait_event_type,
    wait_event,
    state,
    query
FROM pg_stat_activity
WHERE wait_event IS NOT NULL;



-- =========================================================
-- 4. NOWAIT OPTION
-- =========================================================
-- Instead of waiting indefinitely,
-- transaction fails immediately if row is locked.
-- =========================================================



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SELECT *
FROM accounts
WHERE id = 1
    FOR UPDATE;



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SELECT *
FROM accounts
WHERE id = 1
    FOR UPDATE NOWAIT;

-- Expected error:
--
-- could not obtain lock on row in relation "accounts"
--
-- No waiting occurs.



ROLLBACK;



-- =========================================================
-- SESSION 1
-- =========================================================

ROLLBACK;



-- =========================================================
-- 5. SKIP LOCKED
-- =========================================================
-- Useful for:
-- - job queues
-- - workers
-- - distributed processing
--
-- Locked rows are skipped instead of blocking.
-- =========================================================

BEGIN;

SELECT *
FROM accounts
    FOR UPDATE SKIP LOCKED;

COMMIT;



-- =========================================================
-- =========================================================
-- OPTIMISTIC LOCKING
-- =========================================================
-- =========================================================
--
-- Concept:
-- Assume conflicts are rare.
--
-- Do not lock rows immediately.
--
-- Detect conflict during UPDATE.
--
-- Mechanism:
-- - version column
-- - compare current version during UPDATE
--
-- Advantages:
-- - very high concurrency
-- - no blocking
-- - excellent throughput under low contention
--
-- Disadvantages:
-- - retry logic required
-- - failed transactions under contention
-- - application complexity
--
-- Best when:
-- - conflicts are rare
-- - many concurrent readers
-- - high scalability needed
--
-- =========================================================



-- =========================================================
-- 6. RESET DATA
-- =========================================================

UPDATE accounts
SET balance = 1000,
    version = 1
WHERE id = 1;



SELECT *
FROM accounts;



-- =========================================================
-- 7. OPTIMISTIC LOCKING READ PHASE
-- =========================================================
-- Both sessions read:
-- - balance
-- - version
--
-- Both initially see:
-- balance = 1000
-- version = 1
-- =========================================================



-- =========================================================
-- SESSION 1
-- =========================================================

SELECT *
FROM accounts
WHERE id = 1;

-- Application stores:
-- balance = 1000
-- version = 1



-- =========================================================
-- SESSION 2
-- =========================================================

SELECT *
FROM accounts
WHERE id = 1;

-- Also sees:
-- balance = 1000
-- version = 1



-- =========================================================
-- 8. SESSION 1 SUCCESSFUL UPDATE
-- =========================================================
-- Update succeeds because:
-- current version = expected version
-- =========================================================

UPDATE accounts
SET
    balance = balance - 100,
    version = version + 1
WHERE
    id = 1
  AND version = 1;

-- Rows affected: 1



SELECT *
FROM accounts;



-- =========================================================
-- 9. SESSION 2 CONFLICT
-- =========================================================
-- SESSION 2 still expects version = 1,
-- but database row now has version = 2.
--
-- UPDATE affects 0 rows.
-- This is optimistic conflict detection.
-- =========================================================

UPDATE accounts
SET
    balance = balance - 200,
    version = version + 1
WHERE
    id = 1
  AND version = 1;

-- Rows affected: 0
--
-- Conflict detected.
--
-- Application must:
-- - reload latest state
-- - retry business logic
-- - retry transaction



SELECT *
FROM accounts;



-- =========================================================
-- 10. RETRY LOGIC
-- =========================================================
-- Typical optimistic locking workflow:
--
-- 1. Read row + version
-- 2. Perform business logic
-- 3. UPDATE ... WHERE version = ?
-- 4. If rows affected = 0:
--      -> reload
--      -> retry
--
-- This is standard optimistic concurrency control.
-- =========================================================



-- =========================================================
-- 11. SUCCESSFUL RETRY
-- =========================================================
-- SESSION 2 reloads latest version first.
-- =========================================================

SELECT *
FROM accounts
WHERE id = 1;

-- Current state:
-- balance = 900
-- version = 2



UPDATE accounts
SET
    balance = balance - 200,
    version = version + 1
WHERE
    id = 1
  AND version = 2;

-- Rows affected: 1



SELECT *
FROM accounts;

-- Final balance:
-- 700
--
-- Final version:
-- 3



-- =========================================================
-- 12. WHY OPTIMISTIC LOCKING SCALES BETTER
-- =========================================================
-- Pessimistic locking:
-- - transactions wait
-- - threads block
-- - locks accumulate
-- - throughput decreases under contention
--
-- Optimistic locking:
-- - no waiting
-- - no row locks during read phase
-- - transactions execute concurrently
--
-- Only conflicting writes fail.
--
-- Under low contention:
-- optimistic locking usually provides:
-- - higher TPS
-- - lower latency
-- - better scalability
-- =========================================================



-- =========================================================
-- 13. HIGH CONTENTION SCENARIO
-- =========================================================
-- Under heavy write contention:
-- - optimistic retries increase
-- - wasted work increases
-- - retry storms may occur
--
-- In such workloads pessimistic locking
-- may actually perform better.
-- =========================================================



-- =========================================================
-- 14. SIMULATED HIGH CONTENTION
-- =========================================================
-- Run this simultaneously in many sessions
-- to observe retry conflicts.
-- =========================================================

UPDATE accounts
SET
    balance = balance - 1,
    version = version + 1
WHERE
    id = 1
  AND version = (
    SELECT version
    FROM accounts
    WHERE id = 1
);



-- =========================================================
-- 15. DEADLOCK EXAMPLE (PESSIMISTIC)
-- =========================================================
-- Pessimistic locking introduces deadlock risk.
-- =========================================================

INSERT INTO accounts (
    id,
    owner_name,
    balance,
    version
)
VALUES (
           2,
           'Bob',
           1000,
           1
       )
    ON CONFLICT DO NOTHING;



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SELECT *
FROM accounts
WHERE id = 1
    FOR UPDATE;



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SELECT *
FROM accounts
WHERE id = 2
    FOR UPDATE;



-- =========================================================
-- SESSION 1
-- =========================================================

SELECT *
FROM accounts
WHERE id = 2
    FOR UPDATE;

-- SESSION 1 waits.



-- =========================================================
-- SESSION 2
-- =========================================================

SELECT *
FROM accounts
WHERE id = 1
    FOR UPDATE;

-- PostgreSQL detects deadlock.
--
-- One transaction gets:
--
-- ERROR:
-- deadlock detected
-- =========================================================



-- =========================================================
-- 16. DEADLOCK PREVENTION
-- =========================================================
-- Common strategy:
-- always lock rows in deterministic order.
--
-- Example:
-- always lock smaller account_id first.
-- =========================================================



-- =========================================================
-- 17. FINAL STATE CHECK
-- =========================================================

SELECT *
FROM accounts
ORDER BY id;



-- =========================================================
-- 18. PRACTICAL RECOMMENDATIONS
-- =========================================================
--
-- Use pessimistic locking when:
-- - conflicts are frequent
-- - strong consistency required
-- - transactions short
-- - retrying is expensive
--
-- Use optimistic locking when:
-- - conflicts are rare
-- - scalability matters
-- - many concurrent clients
-- - retries are acceptable
--
-- Most large-scale systems:
-- - prefer optimistic approaches
-- - because blocking does not scale well
--
-- But financial / inventory systems often still rely on:
-- - pessimistic locking
-- - SERIALIZABLE transactions
-- - explicit row locks
--
-- Choice depends entirely on workload characteristics.
-- =========================================================