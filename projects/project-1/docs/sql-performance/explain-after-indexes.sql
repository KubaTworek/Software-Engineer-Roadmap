-- Run after the performance dataset is generated.
-- Replace UUID placeholders with values from your local database.

CREATE INDEX IF NOT EXISTS idx_event_city_category_start_time
    ON events(city, category, starts_at);

CREATE INDEX IF NOT EXISTS idx_reservation_org_status_created_at
    ON reservations(organization_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reservation_customer_created_at_id
    ON reservations(customer_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_reservation_event_created_at
    ON reservations(event_id, created_at DESC);

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

-- Keyset pagination: no large OFFSET. Use cursor from previous page.
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.event_id, e.name, r.customer_id, c.email, r.status, r.created_at
  FROM reservations r
  JOIN events e ON e.id = r.event_id
  JOIN customers c ON c.id = r.customer_id
 WHERE r.customer_id = '00000000-0000-0000-0000-000000000000'
   AND r.created_at < TIMESTAMPTZ '2026-06-01T00:00:00Z'
 ORDER BY r.created_at DESC, r.id DESC
 LIMIT 50;
