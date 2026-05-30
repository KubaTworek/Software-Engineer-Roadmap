-- Run on a disposable local database only.
-- This intentionally removes Stage 5 indexes so you can observe sequential scans / expensive sorts.

DROP INDEX IF EXISTS idx_event_city_category_start_time;
DROP INDEX IF EXISTS idx_events_city_category_starts_at;
DROP INDEX IF EXISTS idx_reservation_org_status_created_at;
DROP INDEX IF EXISTS idx_reservation_customer_created_at_id;
DROP INDEX IF EXISTS idx_reservation_customer_created_at;
DROP INDEX IF EXISTS idx_reservation_event_created_at;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, name, city, category, starts_at, status
  FROM events
 WHERE city = 'Warsaw'
   AND category = 'music'
   AND starts_at >= TIMESTAMPTZ '2026-06-01T00:00:00Z'
 ORDER BY starts_at ASC;

EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.event_id, e.name, r.customer_id, c.email, r.status, r.created_at
  FROM reservations r
  JOIN events e ON e.id = r.event_id
  JOIN customers c ON c.id = r.customer_id
 WHERE r.organization_id = '00000000-0000-0000-0000-000000000000'
   AND r.status = 'CONFIRMED'
 ORDER BY r.created_at DESC
 LIMIT 50 OFFSET 50000;

EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.event_id, e.name, r.customer_id, c.email, r.status, r.created_at
  FROM reservations r
  JOIN events e ON e.id = r.event_id
  JOIN customers c ON c.id = r.customer_id
 WHERE r.customer_id = '00000000-0000-0000-0000-000000000000'
 ORDER BY r.created_at DESC
 LIMIT 50 OFFSET 50000;
