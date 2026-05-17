-- =========================================================
-- EXECUTION PLAN AS SOURCE OF TRUTH
-- PostgreSQL / EXPLAIN ANALYZE practical examples
-- =========================================================
-- Goal:
-- Show how to verify whether an optimization really works.
--
-- Key things to inspect:
-- - Seq Scan vs Index Scan
-- - Index Cond vs Filter
-- - Sort operation
-- - Nested Loop vs Hash Join / Merge Join
-- - estimated rows vs actual rows
-- - Execution Time before and after optimization
-- =========================================================



-- =========================================================
-- 1. BASIC TABLE SETUP
-- =========================================================

DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       email TEXT NOT NULL,
                       name TEXT NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        status TEXT NOT NULL,
                        total_amount NUMERIC(12,2) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
);



-- =========================================================
-- 2. SAMPLE DATA
-- =========================================================
-- Generate enough rows so that query plans become meaningful.
-- Small tables often use Seq Scan because it is cheaper.
-- =========================================================

INSERT INTO users (email, name, created_at)
SELECT
    'user_' || gs || '@example.com',
    'User ' || gs,
    NOW() - (gs || ' minutes')::INTERVAL
FROM generate_series(1, 100000) AS gs;


INSERT INTO orders (user_id, status, total_amount, created_at)
SELECT
    (random() * 99999 + 1)::BIGINT,
    CASE
        WHEN random() < 0.2 THEN 'NEW'
        WHEN random() < 0.4 THEN 'PAID'
        WHEN random() < 0.6 THEN 'CANCELLED'
        WHEN random() < 0.8 THEN 'SHIPPED'
        ELSE 'REFUNDED'
        END,
    (random() * 1000)::NUMERIC(12,2),
    NOW() - ((random() * 365) || ' days')::INTERVAL
FROM generate_series(1, 1000000);



-- =========================================================
-- 3. UPDATE STATISTICS
-- =========================================================
-- PostgreSQL planner depends heavily on statistics.
-- After large inserts, always analyze the tables.
-- =========================================================

ANALYZE users;
ANALYZE orders;



-- =========================================================
-- 4. BASELINE: QUERY WITHOUT INDEX
-- =========================================================
-- Expected:
-- - Seq Scan on orders
-- - Filter: user_id = 123
-- - Many rows scanned, few returned
--
-- Important:
-- Filter means PostgreSQL read rows first and then checked condition.
-- This is worse than Index Cond, where index narrows the scan directly.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 123;



-- =========================================================
-- 5. ADD INDEX AND MEASURE AGAIN
-- =========================================================
-- Expected:
-- - Bitmap Index Scan or Index Scan
-- - Index Cond: (user_id = 123)
-- - Much lower Execution Time
-- =========================================================

CREATE INDEX idx_orders_user_id
    ON orders(user_id);

ANALYZE orders;

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 123;



-- =========================================================
-- 6. INDEX COND VS FILTER
-- =========================================================
-- Good plan:
-- Index Cond means the index is used to locate matching rows.
--
-- Less optimal plan:
-- Filter means PostgreSQL still had to inspect extra rows
-- after reading them.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 123
  AND total_amount > 900;



-- =========================================================
-- 7. BETTER INDEX FOR MULTIPLE CONDITIONS
-- =========================================================
-- user_id is equality condition.
-- total_amount is range condition.
--
-- Composite index can reduce extra filtering.
-- =========================================================

CREATE INDEX idx_orders_user_total
    ON orders(user_id, total_amount);

ANALYZE orders;

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 123
  AND total_amount > 900;



-- =========================================================
-- 8. ORDER BY WITHOUT SUPPORTING INDEX
-- =========================================================
-- Expected:
-- - Index Scan or Bitmap Scan on user_id
-- - Sort step for created_at DESC
--
-- Sort can become expensive for large result sets.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 123
ORDER BY created_at DESC;



-- =========================================================
-- 9. INDEX SUPPORTING WHERE + ORDER BY
-- =========================================================
-- Composite index:
-- - user_id supports WHERE
-- - created_at DESC supports ORDER BY
--
-- Expected:
-- - Index Scan using idx_orders_user_created_desc
-- - No separate Sort step
-- =========================================================

CREATE INDEX idx_orders_user_created_desc
    ON orders(user_id, created_at DESC);

ANALYZE orders;

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 123
ORDER BY created_at DESC;



-- =========================================================
-- 10. OFFSET PAGINATION
-- =========================================================
-- Problem:
-- PostgreSQL still has to walk through skipped rows.
--
-- OFFSET 50000 means:
-- - find rows
-- - sort/order them
-- - skip first 50000
-- - return next 50
--
-- Cost grows linearly with OFFSET.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
ORDER BY id
OFFSET 50000
    LIMIT 50;



-- =========================================================
-- 11. KEYSET PAGINATION
-- =========================================================
-- Better approach:
-- remember last seen id from previous page.
--
-- Instead of skipping rows,
-- PostgreSQL seeks directly after last id.
--
-- Expected:
-- - Index Scan on primary key
-- - Much less work than OFFSET
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE id > 50000
ORDER BY id
    LIMIT 50;



-- =========================================================
-- 12. DEEP OFFSET EXAMPLE
-- =========================================================
-- This gets worse as OFFSET grows.
-- Compare Execution Time with keyset pagination below.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
ORDER BY id
OFFSET 900000
    LIMIT 50;



-- =========================================================
-- 13. DEEP KEYSET PAGINATION EXAMPLE
-- =========================================================
-- Still efficient because PostgreSQL can start from id > 900000.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE id > 900000
ORDER BY id
    LIMIT 50;



-- =========================================================
-- 14. JOIN WITHOUT FOREIGN KEY INDEX
-- =========================================================
-- We already created idx_orders_user_id above.
-- Drop it temporarily to observe worse join plan.
--
-- WARNING:
-- This may be slow on large data.
-- =========================================================

DROP INDEX IF EXISTS idx_orders_user_id;
DROP INDEX IF EXISTS idx_orders_user_total;
DROP INDEX IF EXISTS idx_orders_user_created_desc;

ANALYZE orders;

EXPLAIN ANALYZE
SELECT
    u.id,
    u.email,
    o.id AS order_id,
    o.total_amount
FROM users u
         JOIN orders o
              ON o.user_id = u.id
WHERE u.id = 123;



-- =========================================================
-- 15. JOIN WITH FOREIGN KEY INDEX
-- =========================================================
-- Recreate index on orders.user_id.
--
-- Expected:
-- - faster join
-- - index access on orders.user_id
-- - fewer scanned rows
-- =========================================================

CREATE INDEX idx_orders_user_id
    ON orders(user_id);

ANALYZE orders;

EXPLAIN ANALYZE
SELECT
    u.id,
    u.email,
    o.id AS order_id,
    o.total_amount
FROM users u
         JOIN orders o
              ON o.user_id = u.id
WHERE u.id = 123;



-- =========================================================
-- 16. FILTER ON LOW SELECTIVITY COLUMN
-- =========================================================
-- status has only a few values.
--
-- Even with an index, planner may choose Seq Scan,
-- because many rows match each status.
-- =========================================================

CREATE INDEX idx_orders_status
    ON orders(status);

ANALYZE orders;

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE status = 'PAID';



-- =========================================================
-- 17. PARTIAL INDEX FOR LOW SELECTIVITY COLUMN
-- =========================================================
-- Partial index can help if the query is frequent
-- and targets a specific subset.
--
-- This is useful when:
-- - condition is common in queries
-- - indexed subset is much smaller than full table
-- =========================================================

CREATE INDEX idx_orders_paid_user
    ON orders(user_id)
    WHERE status = 'PAID';

ANALYZE orders;

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE status = 'PAID'
  AND user_id = 123;



-- =========================================================
-- 18. ROW ESTIMATION CHECK
-- =========================================================
-- In EXPLAIN ANALYZE inspect:
--
-- rows=...        -> planner estimate
-- actual rows=... -> real number of rows
--
-- Large mismatch may indicate stale or insufficient statistics.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE status = 'PAID'
  AND user_id = 123;



-- =========================================================
-- 19. REFRESH STATISTICS
-- =========================================================
-- If estimates are wrong, run ANALYZE.
-- For large tables or skewed data, consider increasing statistics target.
-- =========================================================

ANALYZE orders;

ALTER TABLE orders
ALTER COLUMN status SET STATISTICS 1000;

ALTER TABLE orders
ALTER COLUMN user_id SET STATISTICS 1000;

ANALYZE orders;

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE status = 'PAID'
  AND user_id = 123;



-- =========================================================
-- 20. USE BUFFERS FOR I/O VISIBILITY
-- =========================================================
-- BUFFERS shows how many pages were read from:
-- - shared buffers
-- - disk
--
-- This is often more useful than time alone.
-- =========================================================

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM orders
WHERE user_id = 123;



-- =========================================================
-- 21. COMPARE BEFORE / AFTER MANUALLY
-- =========================================================
-- Recommended note format:
--
-- Query:
-- SELECT * FROM orders WHERE user_id = 123;
--
-- Before:
-- Plan: Seq Scan
-- Filter: user_id = 123
-- Rows scanned: ~1,000,000
-- Execution Time: X ms
--
-- After:
-- Plan: Index Scan / Bitmap Index Scan
-- Index Cond: user_id = 123
-- Rows scanned: much lower
-- Execution Time: Y ms
--
-- Conclusion:
-- Index reduced scanned rows and I/O.
-- =========================================================



-- =========================================================
-- 22. FINAL CLEANUP OPTIONAL
-- =========================================================
-- Uncomment if you want to remove test objects.
-- =========================================================

-- DROP TABLE IF EXISTS orders;
-- DROP TABLE IF EXISTS users;