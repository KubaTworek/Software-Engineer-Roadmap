CREATE TABLE notification.contacts (
    customer_id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(255) NOT NULL
);
CREATE TABLE notification.contact_projection (
    customer_id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL
);
CREATE TABLE notification.erasure_registry (
    sequence BIGSERIAL UNIQUE NOT NULL,
    customer_id UUID PRIMARY KEY,
    request_id UUID UNIQUE NOT NULL,
    erased_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE notification.erasure_outbox (
    event_id UUID PRIMARY KEY REFERENCES notification.erasure_registry(request_id),
    customer_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
-- Rejestr zawiera jedynie referencję potrzebną do suppression. Nie usuwamy go
-- automatycznie z TTL, gdy istnieją backupy lub źródła replay sprzed usunięcia.
