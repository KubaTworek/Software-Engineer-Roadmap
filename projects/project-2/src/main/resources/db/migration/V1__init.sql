CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS ordering;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS fulfillment;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS integration;

CREATE TABLE IF NOT EXISTS catalog.products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ordering.orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_reserved BOOLEAN NOT NULL,
    stock_reserved BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ordering.order_lines (
    order_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    unit_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    PRIMARY KEY (order_id, line_number),

    CONSTRAINT fk_order_lines_order
    FOREIGN KEY (order_id)
    REFERENCES ordering.orders(id)
    ON DELETE CASCADE,

    CONSTRAINT order_lines_quantity_positive
    CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_order_lines_order_id
    ON ordering.order_lines(order_id);

CREATE TABLE IF NOT EXISTS payment.payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT payments_amount_non_negative
    CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id
    ON payment.payments(order_id);

CREATE TABLE IF NOT EXISTS inventory.stock_items (
    product_id UUID PRIMARY KEY,
    available_quantity INTEGER NOT NULL,
    reserved_quantity INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT stock_items_available_quantity_non_negative
    CHECK (available_quantity >= 0),

    CONSTRAINT stock_items_reserved_quantity_non_negative
    CHECK (reserved_quantity >= 0)
);

CREATE TABLE IF NOT EXISTS fulfillment.shipments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS integration.outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE TABLE IF NOT EXISTS integration.processed_events (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,

    PRIMARY KEY (event_id, consumer_name)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_consumer_name
    ON integration.processed_events(consumer_name);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON integration.processed_events(processed_at);

CREATE TABLE IF NOT EXISTS integration.dead_letter_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    consumer_group VARCHAR(255) NOT NULL,
    kafka_offset BIGINT NOT NULL,
    envelope JSONB NOT NULL,
    reason TEXT,
    attempts INTEGER NOT NULL,
    failed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    replayed_at TIMESTAMP WITH TIME ZONE,
    replay_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_dead_letter_events_status
    ON integration.dead_letter_events(status);

CREATE INDEX IF NOT EXISTS idx_dead_letter_events_failed_at
    ON integration.dead_letter_events(failed_at);

CREATE INDEX IF NOT EXISTS idx_dead_letter_events_topic_consumer_group
    ON integration.dead_letter_events(topic, consumer_group);