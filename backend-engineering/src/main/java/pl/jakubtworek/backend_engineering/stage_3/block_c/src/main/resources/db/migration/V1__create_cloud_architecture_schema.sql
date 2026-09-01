CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(19, 2) NOT NULL CHECK (price >= 0)
);

CREATE INDEX idx_products_name ON products (name);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_orders_customer_created_at ON orders (customer_id, created_at);

CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    dead_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_events (published_at, dead_at, next_attempt_at, created_at);

CREATE TABLE processed_order_events (
    order_id BIGINT PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
