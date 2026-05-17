-- =========================================================
-- TRANSACTIONS AND ISOLATION LEVELS
-- PostgreSQL practical examples
-- =========================================================
-- Goal:
-- Demonstrate common transaction anomalies:
-- - non-repeatable read
-- - phantom read
-- - lost update pattern
-- - write skew
--
-- Tested conceptually for PostgreSQL.
--
-- IMPORTANT:
-- Most examples require two separate database sessions.
-- Open two SQL consoles:
-- - SESSION 1
-- - SESSION 2
--
-- Execute the blocks step by step in the shown order.
-- =========================================================



-- =========================================================
-- 0. CLEAN SETUP
-- =========================================================

DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS on_call;

CREATE TABLE accounts (
                          id BIGINT PRIMARY KEY,
                          owner_name TEXT NOT NULL,
                          balance INTEGER NOT NULL
);

CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL
);

CREATE TABLE on_call (
                         doctor_id BIGINT PRIMARY KEY,
                         doctor_name TEXT NOT NULL,
                         is_on_call BOOLEAN NOT NULL
);



INSERT INTO accounts (id, owner_name, balance)
VALUES
    (1, 'Alice', 1000),
    (2, 'Bob', 1000);



INSERT INTO orders (user_id, status, created_at)
VALUES
    (1, 'PAID', '2025-01-10'),
    (1, 'PAID', '2025-01-11'),
    (2, 'PAID', '2025-01-12'),
    (2, 'CANCELLED', '2025-01-13'),
    (3, 'PENDING', '2025-01-14');



INSERT INTO on_call (doctor_id, doctor_name, is_on_call)
VALUES
    (1, 'Doctor A', TRUE),
    (2, 'Doctor B', TRUE);



-- =========================================================
-- 1. CHECK DEFAULT ISOLATION LEVEL
-- =========================================================
-- PostgreSQL default isolation level is READ COMMITTED.
-- =========================================================

SHOW transaction_isolation;



-- =========================================================
-- 2. READ COMMITTED SNAPSHOT BEHAVIOR
-- =========================================================
-- In READ COMMITTED, every SELECT sees a snapshot from
-- the start of that statement, not from the start of
-- the whole transaction.
--
-- Result:
-- two SELECTs inside one transaction may see different data
-- if another transaction commits between them.
-- =========================================================



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT balance
FROM accounts
WHERE id = 1;

-- Keep this transaction open.
-- Now go to SESSION 2 and update the same row.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

UPDATE accounts
SET balance = 1200
WHERE id = 1;

COMMIT;



-- =========================================================
-- SESSION 1
-- =========================================================
-- Run the same SELECT again.
-- In READ COMMITTED this SELECT can see the new committed value.
-- This is a non-repeatable read.
-- =========================================================

SELECT balance
FROM accounts
WHERE id = 1;

COMMIT;



-- =========================================================
-- 3. REPEATABLE READ SNAPSHOT BEHAVIOR
-- =========================================================
-- In PostgreSQL REPEATABLE READ gives one consistent snapshot
-- for the whole transaction.
--
-- Result:
-- two SELECTs inside one transaction see the same data,
-- even if another transaction commits in the meantime.
-- =========================================================



-- Reset data.

UPDATE accounts
SET balance = 1000
WHERE id = 1;



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;

SELECT balance
FROM accounts
WHERE id = 1;

-- Keep this transaction open.
-- Now go to SESSION 2.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

UPDATE accounts
SET balance = 1200
WHERE id = 1;

COMMIT;



-- =========================================================
-- SESSION 1
-- =========================================================
-- Same SELECT still returns the old value from transaction snapshot.
-- No non-repeatable read.
-- =========================================================

SELECT balance
FROM accounts
WHERE id = 1;

COMMIT;



-- =========================================================
-- 4. PHANTOM READ IN READ COMMITTED
-- =========================================================
-- Phantom read:
-- the same WHERE condition returns a different set of rows
-- because another transaction inserted/deleted matching rows.
-- =========================================================



-- Reset orders.

TRUNCATE orders RESTART IDENTITY;

INSERT INTO orders (user_id, status, created_at)
VALUES
    (1, 'PAID', '2025-01-10'),
    (1, 'PAID', '2025-01-11'),
    (2, 'PAID', '2025-01-12'),
    (2, 'CANCELLED', '2025-01-13'),
    (3, 'PENDING', '2025-01-14');



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT COUNT(*)
FROM orders
WHERE created_at >= '2025-01-01';

-- Expected count: 5.
-- Keep transaction open and go to SESSION 2.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

INSERT INTO orders (user_id, status, created_at)
VALUES (4, 'PENDING', '2025-02-15');

COMMIT;



-- =========================================================
-- SESSION 1
-- =========================================================
-- Same predicate now returns 6.
-- This is a phantom read.
-- =========================================================

SELECT COUNT(*)
FROM orders
WHERE created_at >= '2025-01-01';

COMMIT;



-- =========================================================
-- 5. NO PHANTOM READ IN POSTGRESQL REPEATABLE READ
-- =========================================================
-- PostgreSQL REPEATABLE READ uses one transaction-level snapshot.
-- The second SELECT sees the same snapshot as the first SELECT.
-- =========================================================



-- Reset orders.

TRUNCATE orders RESTART IDENTITY;

INSERT INTO orders (user_id, status, created_at)
VALUES
    (1, 'PAID', '2025-01-10'),
    (1, 'PAID', '2025-01-11'),
    (2, 'PAID', '2025-01-12'),
    (2, 'CANCELLED', '2025-01-13'),
    (3, 'PENDING', '2025-01-14');



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;

SELECT COUNT(*)
FROM orders
WHERE created_at >= '2025-01-01';

-- Expected count: 5.
-- Keep transaction open and go to SESSION 2.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

INSERT INTO orders (user_id, status, created_at)
VALUES (4, 'PENDING', '2025-02-15');

COMMIT;



-- =========================================================
-- SESSION 1
-- =========================================================
-- Same predicate still returns 5.
-- PostgreSQL REPEATABLE READ prevents this phantom effect.
-- =========================================================

SELECT COUNT(*)
FROM orders
WHERE created_at >= '2025-01-01';

COMMIT;



-- =========================================================
-- 6. LOST UPDATE PATTERN
-- =========================================================
-- This demonstrates the dangerous application-level pattern:
-- read value into application memory,
-- calculate new value outside the database,
-- write absolute value back.
--
-- WARNING:
-- PostgreSQL protects some direct concurrent UPDATE conflicts,
-- but lost updates can still occur when applications overwrite
-- values based on stale reads.
-- =========================================================



-- Reset balance.

UPDATE accounts
SET balance = 1000
WHERE id = 1;



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT balance
FROM accounts
WHERE id = 1;

-- Application calculates:
-- new_balance = 1000 - 100 = 900
-- Keep transaction open and go to SESSION 2.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT balance
FROM accounts
WHERE id = 1;

-- Application calculates:
-- new_balance = 1000 - 200 = 800

UPDATE accounts
SET balance = 800
WHERE id = 1;

COMMIT;



-- =========================================================
-- SESSION 1
-- =========================================================
-- Session 1 writes stale calculated value.
-- Final result becomes 900.
--
-- The deduction of 200 from SESSION 2 is overwritten.
-- This is a lost update pattern caused by stale read + absolute write.
-- =========================================================

UPDATE accounts
SET balance = 900
WHERE id = 1;

COMMIT;



-- Check final state.

SELECT *
FROM accounts
WHERE id = 1;



-- =========================================================
-- 7. SAFER UPDATE: IN-DATABASE ARITHMETIC
-- =========================================================
-- Better pattern:
-- let the database update based on current row value.
--
-- UPDATE accounts SET balance = balance - X
--
-- This avoids stale absolute overwrites for simple counters/balances.
-- =========================================================

UPDATE accounts
SET balance = 1000
WHERE id = 1;



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

UPDATE accounts
SET balance = balance - 100
WHERE id = 1;

-- Keep transaction open before COMMIT.
-- SESSION 2 will wait on the same row.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

UPDATE accounts
SET balance = balance - 200
WHERE id = 1;

-- This may block until SESSION 1 commits.



-- =========================================================
-- SESSION 1
-- =========================================================

COMMIT;



-- =========================================================
-- SESSION 2
-- =========================================================
-- After SESSION 1 commits, PostgreSQL applies this update
-- to the current row version.
-- =========================================================

COMMIT;



-- Final balance should be 700.

SELECT *
FROM accounts
WHERE id = 1;



-- =========================================================
-- 8. SAFER UPDATE: SELECT FOR UPDATE
-- =========================================================
-- SELECT FOR UPDATE locks selected rows.
-- Other transactions attempting to update them must wait.
-- Useful when application needs to:
-- - read value
-- - validate business rule
-- - write based on read value
-- =========================================================

UPDATE accounts
SET balance = 1000
WHERE id = 1;



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT balance
FROM accounts
WHERE id = 1
    FOR UPDATE;

-- Row is now locked.
-- Application calculates new balance = 900.
-- Keep transaction open.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT balance
FROM accounts
WHERE id = 1
    FOR UPDATE;

-- This blocks until SESSION 1 commits.



-- =========================================================
-- SESSION 1
-- =========================================================

UPDATE accounts
SET balance = 900
WHERE id = 1;

COMMIT;



-- =========================================================
-- SESSION 2
-- =========================================================
-- After lock is released, SESSION 2 sees the latest committed row.
-- Application should recalculate based on current balance.
-- =========================================================

SELECT balance
FROM accounts
WHERE id = 1;

UPDATE accounts
SET balance = balance - 200
WHERE id = 1;

COMMIT;



-- Final balance should be 700.

SELECT *
FROM accounts
WHERE id = 1;



-- =========================================================
-- 9. WRITE SKEW UNDER REPEATABLE READ
-- =========================================================
-- Write skew:
-- two transactions read the same invariant,
-- then update different rows,
-- so no direct row-level write conflict occurs.
--
-- Example invariant:
-- At least one doctor must remain on call.
--
-- Initial state:
-- doctor 1 is on call
-- doctor 2 is on call
--
-- T1 sees 2 doctors on call and turns doctor 1 off.
-- T2 sees 2 doctors on call and turns doctor 2 off.
--
-- Both transactions commit under REPEATABLE READ.
-- Final state violates invariant: 0 doctors on call.
-- =========================================================



-- Reset on_call table.

UPDATE on_call
SET is_on_call = TRUE;



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;

SELECT COUNT(*)
FROM on_call
WHERE is_on_call = TRUE;

-- Returns 2.
-- Transaction decides doctor 1 can go off call.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;

SELECT COUNT(*)
FROM on_call
WHERE is_on_call = TRUE;

-- Also returns 2.
-- Transaction decides doctor 2 can go off call.



-- =========================================================
-- SESSION 1
-- =========================================================

UPDATE on_call
SET is_on_call = FALSE
WHERE doctor_id = 1;

COMMIT;



-- =========================================================
-- SESSION 2
-- =========================================================

UPDATE on_call
SET is_on_call = FALSE
WHERE doctor_id = 2;

COMMIT;



-- Check violated invariant.

SELECT *
FROM on_call;

SELECT COUNT(*)
FROM on_call
WHERE is_on_call = TRUE;



-- =========================================================
-- 10. WRITE SKEW PREVENTED BY SERIALIZABLE
-- =========================================================
-- PostgreSQL SERIALIZABLE uses SSI
-- Serializable Snapshot Isolation.
--
-- In this scenario one transaction should fail with:
-- could not serialize access due to read/write dependencies
--
-- Application must catch this error and retry the transaction.
-- =========================================================



-- Reset on_call table.

UPDATE on_call
SET is_on_call = TRUE;



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SELECT COUNT(*)
FROM on_call
WHERE is_on_call = TRUE;

-- Returns 2.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SELECT COUNT(*)
FROM on_call
WHERE is_on_call = TRUE;

-- Returns 2.



-- =========================================================
-- SESSION 1
-- =========================================================

UPDATE on_call
SET is_on_call = FALSE
WHERE doctor_id = 1;

COMMIT;



-- =========================================================
-- SESSION 2
-- =========================================================
-- This COMMIT is expected to fail in PostgreSQL SERIALIZABLE mode.
-- Error example:
-- could not serialize access due to read/write dependencies among transactions
--
-- Correct application behavior:
-- ROLLBACK and retry the whole transaction.
-- =========================================================

UPDATE on_call
SET is_on_call = FALSE
WHERE doctor_id = 2;

COMMIT;



-- If SESSION 2 failed, run:

ROLLBACK;



-- Check invariant.

SELECT *
FROM on_call;

SELECT COUNT(*)
FROM on_call
WHERE is_on_call = TRUE;



-- =========================================================
-- 11. DIRTY READS DO NOT OCCUR IN POSTGRESQL READ COMMITTED
-- =========================================================
-- PostgreSQL READ COMMITTED does not allow dirty reads.
-- A transaction cannot see uncommitted changes from another transaction.
-- =========================================================



-- Reset data.

UPDATE accounts
SET balance = 1000
WHERE id = 1;



-- =========================================================
-- SESSION 1
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

UPDATE accounts
SET balance = 500
WHERE id = 1;

-- Do not commit yet.
-- Go to SESSION 2.



-- =========================================================
-- SESSION 2
-- =========================================================

BEGIN;

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT balance
FROM accounts
WHERE id = 1;

-- This still shows committed value, not uncommitted 500.
-- No dirty read.

COMMIT;



-- =========================================================
-- SESSION 1
-- =========================================================

ROLLBACK;



-- Final value remains unchanged.

SELECT *
FROM accounts
WHERE id = 1;



-- =========================================================
-- 12. SUMMARY QUERY CHECKS
-- =========================================================

SELECT *
FROM accounts
ORDER BY id;

SELECT *
FROM orders
ORDER BY id;

SELECT *
FROM on_call
ORDER BY doctor_id;