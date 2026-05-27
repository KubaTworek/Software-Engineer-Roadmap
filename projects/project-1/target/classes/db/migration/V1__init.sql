CREATE TABLE events (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(120) NOT NULL,
    category VARCHAR(120) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE capacity_pools (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE REFERENCES events(id),
    total_capacity INTEGER NOT NULL CHECK (total_capacity > 0),
    available_capacity INTEGER NOT NULL CHECK (available_capacity >= 0),
    version BIGINT NOT NULL
);

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);

CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_events_city_category_starts_at
    ON events(city, category, starts_at);

CREATE INDEX idx_reservations_event_status_created_at
    ON reservations(event_id, status, created_at DESC);

CREATE INDEX idx_reservations_customer_created_at
    ON reservations(customer_id, created_at DESC);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    type VARCHAR(80) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbound_messages (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    channel VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    payload VARCHAR(1000) NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_logs_reservation_id
    ON audit_logs(reservation_id);

CREATE INDEX idx_outbound_messages_reservation_id
    ON outbound_messages(reservation_id);
