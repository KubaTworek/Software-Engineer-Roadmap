ALTER TABLE events
    ADD COLUMN organization_id UUID REFERENCES organizations(id);

CREATE INDEX IF NOT EXISTS idx_event_city_category_start_time
    ON events(city, category, starts_at);

CREATE INDEX IF NOT EXISTS idx_event_organization_starts_at
    ON events(organization_id, starts_at DESC);

ALTER TABLE reservations
    ADD COLUMN organization_id UUID REFERENCES organizations(id);

UPDATE reservations r
   SET organization_id = e.organization_id
  FROM events e
 WHERE r.event_id = e.id
   AND e.organization_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_reservation_org_status_created_at
    ON reservations(organization_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reservation_customer_created_at_id
    ON reservations(customer_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_reservation_event_created_at
    ON reservations(event_id, created_at DESC);
