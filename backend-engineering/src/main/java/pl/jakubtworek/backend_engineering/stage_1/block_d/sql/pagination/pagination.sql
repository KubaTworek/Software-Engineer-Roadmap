-- =========================================================
-- OFFSET VS KEYSET PAGINATION
-- PostgreSQL practical demo
-- =========================================================
-- Goal:
-- Demonstrate why OFFSET pagination becomes slower
-- as OFFSET grows, and why keyset / seek pagination
-- stays fast for next-page navigation.
--
-- Main idea:
-- OFFSET N means:
-- - find ordered rows
-- - walk through N rows
-- - discard them
-- - return only LIMIT rows
--
-- Keyset pagination means:
-- - remember last seen key
-- - seek directly after that key using an index
-- - return LIMIT rows
--
-- =========================================================



-- =========================================================
-- 0. CLEAN SETUP
-- =========================================================

DROP TABLE IF EXISTS orders;



CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status TEXT NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);



-- =========================================================
-- 1. SAMPLE DATA
-- =========================================================
-- We generate many rows for one user so that deep pagination
-- becomes visible in EXPLAIN ANALYZE.
--
-- In real systems this could be:
-- - user order history
-- - activity feed
-- - audit log
-- - notifications
-- - messages
-- =========================================================

INSERT INTO orders (
    user_id,
    status,
    total_amount,
    created_at
)
SELECT
    1,
    CASE
        WHEN random() < 0.25 THEN 'NEW'
        WHEN random() < 0.50 THEN 'PAID'
        WHEN random() < 0.75 THEN 'SHIPPED'
        ELSE 'CANCELLED'
    END,
    (random() * 1000)::NUMERIC(12,2),
    NOW() - ((1000000 - gs) || ' seconds')::INTERVAL
FROM generate_series(1, 1000000) AS gs;



-- Additional data for other users.
-- This makes the composite index example more realistic.

INSERT INTO orders (
    user_id,
    status,
    total_amount,
    created_at
)
SELECT
    (random() * 100 + 2)::BIGINT,
    CASE
        WHEN random() < 0.25 THEN 'NEW'
        WHEN random() < 0.50 THEN 'PAID'
        WHEN random() < 0.75 THEN 'SHIPPED'
        ELSE 'CANCELLED'
    END,
    (random() * 1000)::NUMERIC(12,2),
    NOW() - ((random() * 1000000) || ' seconds')::INTERVAL
FROM generate_series(1, 200000) AS gs;



ANALYZE orders;



-- =========================================================
-- 2. INDEXES FOR PAGINATION
-- =========================================================
-- Primary key already creates an index on id.
--
-- For user-scoped pagination:
-- WHERE user_id = ?
-- ORDER BY id
--
-- The best index is usually:
-- (user_id, id)
--
-- This allows PostgreSQL to:
-- - find one user's rows
-- - walk them in id order
-- - seek efficiently after last_id
-- =========================================================

CREATE INDEX idx_orders_user_id_id
ON orders(user_id, id);



-- For time-based feeds:
-- WHERE user_id = ?
-- ORDER BY created_at DESC, id DESC
--
-- This index supports stable keyset pagination by timestamp.
-- id is added as a tie-breaker because many rows can have
-- the same created_at value.
-- =========================================================

CREATE INDEX idx_orders_user_created_id_desc
ON orders(user_id, created_at DESC, id DESC);



ANALYZE orders;



-- =========================================================
-- 3. OFFSET PAGINATION - FIRST PAGE
-- =========================================================
-- This is usually fast.
--
-- PostgreSQL reads only the first 10 rows in index order.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY id
LIMIT 10
OFFSET 0;



-- =========================================================
-- 4. OFFSET PAGINATION - MEDIUM OFFSET
-- =========================================================
-- PostgreSQL must still walk through first 10,000 rows,
-- discard them, and only then return 10 rows.
--
-- Returned rows: 10
-- Work done: roughly 10,010 rows scanned
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY id
LIMIT 10
OFFSET 10000;



-- =========================================================
-- 5. OFFSET PAGINATION - DEEP OFFSET
-- =========================================================
-- PostgreSQL must walk through first 100,000 rows.
--
-- Returned rows: 10
-- Work done: roughly 100,010 rows scanned
--
-- This is the core problem.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY id
LIMIT 10
OFFSET 100000;



-- =========================================================
-- 6. OFFSET PAGINATION - VERY DEEP OFFSET
-- =========================================================
-- This becomes increasingly expensive.
--
-- Even though LIMIT is still 10,
-- PostgreSQL has to skip 500,000 rows first.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY id
LIMIT 10
OFFSET 500000;



-- =========================================================
-- 7. KEYSET PAGINATION - FIRST PAGE
-- =========================================================
-- First page does not need last_id yet.
-- It is equivalent to normal LIMIT query.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY id
LIMIT 10;



-- =========================================================
-- 8. KEYSET PAGINATION - NEXT PAGE
-- =========================================================
-- Application remembers last id from previous page.
--
-- Example:
-- previous page returned last_id = 10
--
-- Instead of OFFSET 10:
-- use WHERE id > 10.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
  AND id > 10
ORDER BY id
LIMIT 10;



-- =========================================================
-- 9. KEYSET PAGINATION - DEEP PAGE
-- =========================================================
-- Suppose the previous page ended at id = 100000.
--
-- PostgreSQL can seek directly into the index:
-- (user_id, id)
--
-- It does NOT have to walk through the first 100,000 rows.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
  AND id > 100000
ORDER BY id
LIMIT 10;



-- =========================================================
-- 10. KEYSET PAGINATION - VERY DEEP PAGE
-- =========================================================
-- Even for very large last_id, PostgreSQL uses the index
-- to seek to the right position and returns the next 10 rows.
--
-- Work depends mostly on LIMIT, not on the page number.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
  AND id > 500000
ORDER BY id
LIMIT 10;



-- =========================================================
-- 11. OFFSET VS KEYSET DIRECT COMPARISON
-- =========================================================
-- Compare these two queries:
--
-- OFFSET:
-- - must skip 500,000 rows
--
-- KEYSET:
-- - seeks after id = 500000
--
-- Both return 10 rows, but the work done is different.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY id
LIMIT 10
OFFSET 500000;



EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
  AND id > 500000
ORDER BY id
LIMIT 10;



-- =========================================================
-- 12. USE BUFFERS TO SEE I/O
-- =========================================================
-- BUFFERS shows page-level work.
--
-- OFFSET usually touches many more pages
-- as OFFSET grows.
-- =========================================================

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY id
LIMIT 10
OFFSET 500000;



EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM orders
WHERE user_id = 1
  AND id > 500000
ORDER BY id
LIMIT 10;



-- =========================================================
-- 13. TIME-BASED KEYSET PAGINATION
-- =========================================================
-- Many feeds are sorted by created_at DESC.
--
-- Problem:
-- created_at alone is not always unique.
--
-- Solution:
-- use composite cursor:
-- (created_at, id)
--
-- ORDER BY:
-- created_at DESC, id DESC
--
-- Cursor condition:
-- (created_at, id) < (:last_created_at, :last_id)
--
-- For descending order, use < to get older rows.
-- =========================================================



-- First page.

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY created_at DESC, id DESC
LIMIT 10;



-- Next page.
-- Replace values below with last row from previous page.
-- Example cursor:
-- last_created_at = '2025-01-01 12:00:00'
-- last_id = 500000
--
-- For DESC order:
-- fetch rows "after" cursor using tuple comparison <
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
  AND (created_at, id) < ('2025-01-01 12:00:00'::timestamp, 500000)
ORDER BY created_at DESC, id DESC
LIMIT 10;



-- =========================================================
-- 14. WHY id TIE-BREAKER MATTERS
-- =========================================================
-- Bad:
-- ORDER BY created_at DESC
--
-- If many rows have same created_at:
-- - order may be unstable
-- - records can be skipped
-- - records can be duplicated between pages
--
-- Good:
-- ORDER BY created_at DESC, id DESC
--
-- id makes ordering deterministic.
-- =========================================================



-- =========================================================
-- 15. BAD KEYSET WITHOUT TIE-BREAKER
-- =========================================================
-- This can be unsafe if created_at is not unique.
-- Shown only as an anti-example.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
  AND created_at < '2025-01-01 12:00:00'::timestamp
ORDER BY created_at DESC
LIMIT 10;



-- =========================================================
-- 16. GOOD KEYSET WITH TIE-BREAKER
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
  AND (created_at, id) < ('2025-01-01 12:00:00'::timestamp, 500000)
ORDER BY created_at DESC, id DESC
LIMIT 10;



-- =========================================================
-- 17. PREVIOUS PAGE WITH KEYSET
-- =========================================================
-- Keyset can support previous page too,
-- but query direction must be reversed.
--
-- Suppose current first row cursor is:
-- first_id = 500000
--
-- For ascending id pagination:
-- previous page:
-- WHERE id < first_id
-- ORDER BY id DESC
-- LIMIT 10
--
-- Application reverses result order after fetching.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
  AND id < 500000
ORDER BY id DESC
LIMIT 10;



-- =========================================================
-- 18. LIMITATION OF KEYSET PAGINATION
-- =========================================================
-- Keyset does NOT easily support:
-- - jump to page 57
-- - total page count
-- - arbitrary random access pages
--
-- It is best for:
-- - next page
-- - previous page
-- - infinite scroll
-- - APIs with cursor pagination
-- =========================================================



-- =========================================================
-- 19. OFFSET STILL HAS VALID USE CASES
-- =========================================================
-- OFFSET can be acceptable when:
-- - tables are small
-- - offsets are tiny
-- - admin UI only
-- - exact page numbers are required
-- - query is rare
--
-- But avoid high OFFSET on large tables.
-- =========================================================



-- =========================================================
-- 20. RESULT COMPARISON TEMPLATE
-- =========================================================
-- Suggested benchmark table:
--
-- Pagination type | Query shape                         | Execution Time
-- ----------------|-------------------------------------|---------------
-- OFFSET 0        | LIMIT 10 OFFSET 0                   | ...
-- OFFSET 10k      | LIMIT 10 OFFSET 10000               | ...
-- OFFSET 100k     | LIMIT 10 OFFSET 100000              | ...
-- OFFSET 500k     | LIMIT 10 OFFSET 500000              | ...
-- KEYSET 10k      | WHERE id > 10000 LIMIT 10           | ...
-- KEYSET 100k     | WHERE id > 100000 LIMIT 10          | ...
-- KEYSET 500k     | WHERE id > 500000 LIMIT 10          | ...
--
-- Expected:
-- - OFFSET grows roughly linearly with offset size
-- - KEYSET stays close to constant for same LIMIT
-- =========================================================



-- =========================================================
-- 21. FINAL RECOMMENDATIONS
-- =========================================================
--
-- Use OFFSET when:
-- - dataset is small
-- - offset is small
-- - random page access is required
-- - admin/reporting interface
--
-- Use KEYSET when:
-- - table is large
-- - API uses next/prev
-- - infinite scroll
-- - performance must stay stable
-- - sort order is deterministic
--
-- Required for keyset:
-- - stable ORDER BY
-- - indexed cursor columns
-- - unique tie-breaker such as id
--
-- Golden rule:
-- OFFSET skips rows.
-- KEYSET seeks rows.
-- =========================================================