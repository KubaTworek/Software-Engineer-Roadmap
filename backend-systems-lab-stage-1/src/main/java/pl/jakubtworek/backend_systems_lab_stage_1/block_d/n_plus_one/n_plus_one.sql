-- =========================================================
-- N+1 QUERY PROBLEM
-- PostgreSQL / ORM practical examples
-- =========================================================
-- Goal:
-- Demonstrate:
-- - N+1 query pattern
-- - join fetch style optimization
-- - batch loading
-- - pagination issues with JOINs
-- - trade-offs between approaches
--
-- This file simulates ORM behavior using plain SQL.
--
-- Real ORMs affected:
-- - Hibernate
-- - JPA
-- - Entity Framework
-- - Django ORM
-- - SQLAlchemy
--
-- =========================================================



-- =========================================================
-- 0. CLEAN SETUP
-- =========================================================

DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;



CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       email TEXT NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT NOW()
);



CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL REFERENCES users(id),
                        status TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
);



CREATE TABLE products (
                          id BIGSERIAL PRIMARY KEY,
                          name TEXT NOT NULL,
                          price NUMERIC(12,2) NOT NULL
);



CREATE TABLE order_items (
                             id BIGSERIAL PRIMARY KEY,
                             order_id BIGINT NOT NULL REFERENCES orders(id),
                             product_id BIGINT NOT NULL REFERENCES products(id),
                             quantity INTEGER NOT NULL,
                             unit_price NUMERIC(12,2) NOT NULL
);



-- =========================================================
-- 1. SAMPLE DATA
-- =========================================================

INSERT INTO users (email)
SELECT
    'user_' || gs || '@example.com'
FROM generate_series(1, 100) AS gs;



INSERT INTO products (name, price)
SELECT
    'Product ' || gs,
    (random() * 100)::NUMERIC(12,2)
FROM generate_series(1, 500) AS gs;



INSERT INTO orders (user_id, status, created_at)
SELECT
    (random() * 99 + 1)::BIGINT,
    CASE
        WHEN random() < 0.5 THEN 'PAID'
        ELSE 'PENDING'
        END,
    NOW() - ((random() * 30) || ' days')::INTERVAL
FROM generate_series(1, 1000);



INSERT INTO order_items (
    order_id,
    product_id,
    quantity,
    unit_price
)
SELECT
    (random() * 999 + 1)::BIGINT,
    (random() * 499 + 1)::BIGINT,
    (random() * 5 + 1)::INTEGER,
    (random() * 100)::NUMERIC(12,2)
FROM generate_series(1, 5000);



ANALYZE;



-- =========================================================
-- 2. TYPICAL ORM N+1 PATTERN
-- =========================================================
-- ORM behavior:
--
-- Query 1:
-- SELECT * FROM orders;
--
-- Then for each order:
-- SELECT * FROM order_items WHERE order_id = ?;
--
-- Total:
-- 1 + N queries.
--
-- =========================================================



-- =========================================================
-- FIRST QUERY
-- =========================================================
-- ORM loads orders list.
-- =========================================================

SELECT *
FROM orders
ORDER BY created_at DESC
    LIMIT 10;



-- =========================================================
-- N ADDITIONAL QUERIES
-- =========================================================
-- ORM lazily loads items for every order.
--
-- Simulated manually below.
-- =========================================================

SELECT *
FROM order_items
WHERE order_id = 1;

SELECT *
FROM order_items
WHERE order_id = 2;

SELECT *
FROM order_items
WHERE order_id = 3;

SELECT *
FROM order_items
WHERE order_id = 4;

SELECT *
FROM order_items
WHERE order_id = 5;



-- =========================================================
-- PROBLEM
-- =========================================================
-- For 10 orders:
-- 11 queries total.
--
-- For 100 orders:
-- 101 queries total.
--
-- For 1000 orders:
-- 1001 queries total.
--
-- Network overhead and latency become dominant.
-- =========================================================



-- =========================================================
-- 3. DETECTING N+1
-- =========================================================
-- Symptoms:
-- - many repeated queries
-- - same SQL shape repeated with different IDs
-- - spikes in DB roundtrips
-- - ORM debug logs flooded with SELECTs
-- =========================================================



-- =========================================================
-- 4. FETCH JOIN / JOIN FETCH SOLUTION
-- =========================================================
-- Instead of:
-- 1 query for orders
-- + N queries for items
--
-- Use one JOIN query.
--
-- Equivalent to Hibernate:
--
-- SELECT o FROM Order o JOIN FETCH o.items
--
-- =========================================================

EXPLAIN ANALYZE
SELECT
    o.id AS order_id,
    o.user_id,
    o.status,
    o.created_at,
    oi.id AS item_id,
    oi.product_id,
    oi.quantity,
    oi.unit_price
FROM orders o
         JOIN order_items oi
              ON oi.order_id = o.id
ORDER BY o.created_at DESC;



-- =========================================================
-- BENEFIT
-- =========================================================
-- Single query.
--
-- Eliminates:
-- - repeated roundtrips
-- - repeated query parsing
-- - repeated connection overhead
--
-- Usually much faster.
-- =========================================================



-- =========================================================
-- 5. PROBLEM OF JOIN FETCH
-- =========================================================
-- JOIN duplicates parent rows.
--
-- Example:
-- order with 10 items appears 10 times.
--
-- Large object graphs can explode result size.
-- =========================================================

SELECT
    o.id,
    oi.id
FROM orders o
         JOIN order_items oi
              ON oi.order_id = o.id
WHERE o.id = 1;



-- =========================================================
-- 6. ROW MULTIPLICATION
-- =========================================================
-- One order:
-- 1 row logically.
--
-- But after JOIN:
-- many physical rows.
--
-- This increases:
-- - network transfer
-- - memory usage
-- - deserialization cost
-- - ORM hydration cost
-- =========================================================



-- =========================================================
-- 7. PAGINATION PROBLEM
-- =========================================================
-- JOIN FETCH can break pagination.
--
-- LIMIT applies to joined rows,
-- not logical parent entities.
-- =========================================================

SELECT
    o.id,
    oi.id
FROM orders o
         JOIN order_items oi
              ON oi.order_id = o.id
ORDER BY o.created_at DESC
    LIMIT 20;



-- =========================================================
-- PROBLEM
-- =========================================================
-- LIMIT 20 does NOT mean:
-- 20 orders.
--
-- It means:
-- 20 joined rows.
--
-- If one order has many items,
-- fewer distinct orders appear.
-- =========================================================



-- =========================================================
-- 8. BETTER PAGINATION STRATEGY
-- =========================================================
-- Step 1:
-- paginate parent IDs only.
-- =========================================================

WITH paged_orders AS (
    SELECT id
    FROM orders
    ORDER BY created_at DESC
    LIMIT 20
    )
SELECT *
FROM orders
WHERE id IN (
    SELECT id
    FROM paged_orders
);



-- =========================================================
-- Step 2:
-- load related entities separately.
-- =========================================================

WITH paged_orders AS (
    SELECT id
    FROM orders
    ORDER BY created_at DESC
    LIMIT 20
    )
SELECT *
FROM order_items
WHERE order_id IN (
    SELECT id
    FROM paged_orders
);



-- =========================================================
-- BENEFIT
-- =========================================================
-- Avoids:
-- - huge cartesian result sets
-- - broken pagination
-- - excessive duplication
-- =========================================================



-- =========================================================
-- 9. BATCH LOADING STRATEGY
-- =========================================================
-- ORM frameworks often support:
-- - @BatchSize
-- - IN batching
--
-- Instead of:
-- 1 query per order
--
-- ORM groups IDs into batches.
-- =========================================================

SELECT *
FROM order_items
WHERE order_id IN (
                   1,2,3,4,5,6,7,8,9,10
    );



-- =========================================================
-- BENEFIT
-- =========================================================
-- Instead of:
-- 10 separate queries
--
-- Use:
-- 1 batched query.
--
-- Good compromise between:
-- - JOIN FETCH
-- - N+1
-- =========================================================



-- =========================================================
-- 10. INDEXES FOR N+1 FIXES
-- =========================================================
-- Relationship columns MUST be indexed.
-- =========================================================

CREATE INDEX idx_order_items_order_id
    ON order_items(order_id);



CREATE INDEX idx_orders_created_at
    ON orders(created_at DESC);



ANALYZE;



-- =========================================================
-- 11. EXPLAIN ANALYZE AFTER INDEXES
-- =========================================================

EXPLAIN ANALYZE
SELECT *
FROM order_items
WHERE order_id IN (
                   1,2,3,4,5,6,7,8,9,10
    );



-- =========================================================
-- 12. LARGE FETCH JOIN MEMORY COST
-- =========================================================
-- Simulate very large join result.
-- =========================================================

EXPLAIN ANALYZE
SELECT
    o.*,
    oi.*,
    p.*
FROM orders o
         JOIN order_items oi
              ON oi.order_id = o.id
         JOIN products p
              ON p.id = oi.product_id;



-- =========================================================
-- PROBLEM
-- =========================================================
-- ORM may need to:
-- - hydrate huge object graph
-- - deduplicate entities
-- - hold everything in memory
--
-- Sometimes:
-- one massive JOIN is worse than batched loading.
-- =========================================================



-- =========================================================
-- 13. WHEN NOT TO OPTIMIZE N+1
-- =========================================================
-- N+1 is not automatically bad.
--
-- If:
-- - N is very small
-- - query is rare
-- - objects are tiny
-- - data cached
--
-- JOIN FETCH may:
-- - increase memory usage
-- - increase response size
-- - complicate pagination
--
-- Always measure real workload.
-- =========================================================



-- =========================================================
-- 14. EXAMPLE: SMALL N
-- =========================================================
-- Small result sets may be perfectly acceptable.
-- =========================================================

SELECT *
FROM orders
         LIMIT 3;



SELECT *
FROM order_items
WHERE order_id IN (1,2,3);



-- =========================================================
-- Sometimes optimization complexity
-- costs more than the original problem.
-- =========================================================



-- =========================================================
-- 15. SUMMARY QUERIES
-- =========================================================

SELECT COUNT(*)
FROM orders;

SELECT COUNT(*)
FROM order_items;



-- =========================================================
-- 16. FINAL RECOMMENDATIONS
-- =========================================================
--
-- Detect N+1 by:
-- - SQL logs
-- - query profilers
-- - repeated SELECT patterns
--
-- Common fixes:
-- - JOIN FETCH
-- - EntityGraph
-- - batch loading
-- - IN batching
--
-- Beware:
-- - memory explosion
-- - row multiplication
-- - broken pagination
--
-- Best solution depends on:
-- - result size
-- - object graph size
-- - cardinality
-- - pagination requirements
-- - workload characteristics
--
-- Always measure:
-- - query count
-- - execution time
-- - memory usage
-- - transferred rows
--
-- There is no universally best strategy.
-- =========================================================