-- =========================================================
-- TABLE SETUP
-- =========================================================

CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       email TEXT NOT NULL,
                       email_lowercase TEXT NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        status TEXT NOT NULL,
                        total_amount NUMERIC(12,2) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE sales (
                       id BIGSERIAL PRIMARY KEY,
                       subsidiary_id BIGINT NOT NULL,
                       eur_value NUMERIC(12,2) NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT NOW()
);



-- =========================================================
-- BASIC B-TREE INDEX
-- =========================================================
-- Purpose:
-- Accelerate equality lookups on user_id.
--
-- Without this index:
-- SELECT * FROM orders WHERE user_id = 1;
-- requires full table scan (Seq Scan).
--
-- With the index:
-- database can directly seek matching rows.
-- =========================================================

CREATE INDEX idx_orders_user_id
    ON orders(user_id);



-- =========================================================
-- QUERY USING INDEX
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1;



-- =========================================================
-- LOW SELECTIVITY INDEX
-- =========================================================
-- WARNING:
-- This index may provide little benefit.
--
-- status has very low cardinality:
-- NEW / PAID / CANCELLED / SHIPPED / REFUNDED
--
-- If query matches large percentage of rows,
-- planner may choose Seq Scan anyway.
-- =========================================================

CREATE INDEX idx_orders_status
    ON orders(status);



EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE status = 'PAID';



-- =========================================================
-- FUNCTION IN WHERE BREAKS INDEX USAGE
-- =========================================================
-- Even if index exists on email,
-- LOWER(email) prevents standard B-Tree usage.
-- =========================================================

CREATE INDEX idx_users_email
    ON users(email);



EXPLAIN ANALYZE
SELECT *
FROM users
WHERE LOWER(email) = 'john@example.com';



-- =========================================================
-- FUNCTIONAL INDEX
-- =========================================================
-- Correct approach when queries consistently
-- apply transformation function.
-- =========================================================

CREATE INDEX idx_users_email_lower
    ON users(LOWER(email));



EXPLAIN ANALYZE
SELECT *
FROM users
WHERE LOWER(email) = 'john@example.com';



-- =========================================================
-- ALTERNATIVE APPROACH:
-- NORMALIZED COLUMN
-- =========================================================
-- Instead of functional index,
-- store normalized value directly.
-- =========================================================

CREATE INDEX idx_users_email_lowercase
    ON users(email_lowercase);



EXPLAIN ANALYZE
SELECT *
FROM users
WHERE email_lowercase = 'john@example.com';



-- =========================================================
-- COVERING INDEX / INDEX-ONLY SCAN
-- =========================================================
-- Index contains:
-- - filtering column
-- - aggregated column
--
-- Database may avoid heap access completely.
-- =========================================================

CREATE INDEX idx_sales_subsidiary_eur
    ON sales(subsidiary_id, eur_value);



EXPLAIN ANALYZE
SELECT SUM(eur_value)
FROM sales
WHERE subsidiary_id = 42;



-- =========================================================
-- JOIN WITHOUT INDEX
-- =========================================================
-- Missing foreign key index may cause:
-- Nested Loop + Seq Scan
--
-- Complexity can degrade heavily for large datasets.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders o
         JOIN users u
              ON o.user_id = u.id;



-- =========================================================
-- INDEX FOR JOIN COLUMN
-- =========================================================
-- Always index foreign keys used in joins.
-- =========================================================

CREATE INDEX idx_orders_user_join
    ON orders(user_id);



EXPLAIN ANALYZE
SELECT *
FROM orders o
         JOIN users u
              ON o.user_id = u.id;



-- =========================================================
-- ORDER BY WITHOUT SUPPORTING INDEX
-- =========================================================
-- Database may require explicit Sort operation.
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY created_at DESC;



-- =========================================================
-- COMPOSITE INDEX SUPPORTING:
-- - filtering
-- - ordering
-- =========================================================

CREATE INDEX idx_orders_user_created
    ON orders(user_id, created_at DESC);



EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY created_at DESC;



-- =========================================================
-- BAD COMPOSITE INDEX ORDER
-- =========================================================
-- Index starts with created_at.
--
-- Query filters only user_id.
--
-- Leftmost prefix rule prevents efficient usage.
-- =========================================================

CREATE INDEX idx_bad
    ON orders(created_at, user_id);



EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1;



-- =========================================================
-- GOOD COMPOSITE INDEX ORDER
-- =========================================================
-- user_id is first.
--
-- Database can:
-- 1. seek by user_id
-- 2. scan created_at in sorted order
-- =========================================================

CREATE INDEX idx_good
    ON orders(user_id, created_at);



EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1;



-- =========================================================
-- COMPOSITE INDEX SUPPORTING:
-- - filtering
-- - ordering
-- - covering
-- =========================================================

CREATE INDEX idx_orders_user_created_status
    ON orders(user_id, created_at DESC, status);



EXPLAIN ANALYZE
SELECT status
FROM orders
WHERE user_id = 1
ORDER BY created_at DESC;



-- =========================================================
-- GROUP BY OPTIMIZATION
-- =========================================================
-- Index ordering may reduce grouping cost.
-- =========================================================

EXPLAIN ANALYZE
SELECT user_id, COUNT(*)
FROM orders
GROUP BY user_id;



CREATE INDEX idx_orders_grouping
    ON orders(user_id);



EXPLAIN ANALYZE
SELECT user_id, COUNT(*)
FROM orders
GROUP BY user_id;



-- =========================================================
-- RANGE SCAN EXAMPLE
-- =========================================================
-- B-Tree indexes support efficient range scans.
-- =========================================================

CREATE INDEX idx_orders_created_at
    ON orders(created_at);



EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE created_at >= '2025-01-01'
  AND created_at < '2025-02-01';



-- =========================================================
-- MULTI-COLUMN RANGE + EQUALITY
-- =========================================================
-- Equality first,
-- range second.
-- =========================================================

CREATE INDEX idx_orders_user_created_range
    ON orders(user_id, created_at);



EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE user_id = 1
  AND created_at >= '2025-01-01'
  AND created_at < '2025-02-01';



-- =========================================================
-- OVER-INDEXING EXAMPLE
-- =========================================================
-- Every INSERT / UPDATE must maintain:
-- - all B-Trees
-- - all index pages
--
-- Too many indexes increase:
-- - write amplification
-- - storage usage
-- - VACUUM cost
-- - maintenance overhead
-- =========================================================

CREATE INDEX idx_orders_status_created
    ON orders(status, created_at);

CREATE INDEX idx_orders_total
    ON orders(total_amount);

CREATE INDEX idx_orders_created_total
    ON orders(created_at, total_amount);

CREATE INDEX idx_orders_status_total
    ON orders(status, total_amount);



-- =========================================================
-- INSERT COST DEMONSTRATION
-- =========================================================
-- Large number of indexes slows writes significantly.
-- =========================================================

INSERT INTO orders (
    user_id,
    status,
    total_amount
)
VALUES (
           1,
           'PAID',
           199.99
       );



-- =========================================================
-- PARTIAL INDEX
-- =========================================================
-- Useful when only subset of rows is queried frequently.
--
-- Much smaller than full index.
-- =========================================================

CREATE INDEX idx_orders_paid_only
    ON orders(user_id)
    WHERE status = 'PAID';



EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE status = 'PAID'
  AND user_id = 1;



-- =========================================================
-- UNIQUE INDEX
-- =========================================================
-- Provides:
-- - fast lookup
-- - uniqueness guarantee
-- =========================================================

CREATE UNIQUE INDEX idx_users_email_unique
    ON users(email);



EXPLAIN ANALYZE
SELECT *
FROM users
WHERE email = 'john@example.com';



-- =========================================================
-- FINAL NOTES
-- =========================================================
-- Indexes optimize:
-- - access patterns
-- - query shapes
-- - specific workloads
--
-- Indexes are NOT free:
-- every additional index increases:
-- - write cost
-- - storage
-- - maintenance
-- - VACUUM overhead
--
-- Golden rule:
-- optimize for real queries,
-- not hypothetical future use cases.
-- =========================================================