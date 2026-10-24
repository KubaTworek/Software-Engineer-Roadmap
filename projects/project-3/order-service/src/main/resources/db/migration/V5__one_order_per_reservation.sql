-- Nie usuwamy konfliktów automatycznie. Istniejące duplikaty wymagają uzgodnienia
-- efektów finansowych przed wdrożeniem tej migracji.
CREATE UNIQUE INDEX ux_orders_reservation ON orders(reservation_id);
UPDATE orders SET next_attempt_at=now()
WHERE status IN ('PENDING','PAYMENT_IN_PROGRESS') AND next_attempt_at IS NULL;
