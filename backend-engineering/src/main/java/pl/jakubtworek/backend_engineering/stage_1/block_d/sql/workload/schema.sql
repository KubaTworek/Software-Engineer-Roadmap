-- Ten skrypt resetuje samodzielne laboratorium. Nie uruchamiaj go na wspólnej bazie.
DROP TABLE IF EXISTS outbox, payments, order_items, orders, accounts, users CASCADE;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL CHECK (btrim(name) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    balance NUMERIC(19, 2) NOT NULL CHECK (balance >= 0),
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    UNIQUE (order_id, product_id)
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'CAPTURED', 'FAILED', 'REFUNDED')),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL CHECK (currency = upper(currency))
);

CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type TEXT NOT NULL CHECK (btrim(aggregate_type) <> ''),
    aggregate_id BIGINT NOT NULL,
    event_type TEXT NOT NULL CHECK (btrim(event_type) <> ''),
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);

-- Indeksy odpowiadają konkretnym access patternom laboratoriów.
CREATE INDEX idx_orders_user_timeline
    ON orders (user_id, created_at DESC, id DESC);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_outbox_unpublished
    ON outbox (created_at, id) WHERE published_at IS NULL;

INSERT INTO users (name, created_at) VALUES
    ('Ala', '2025-01-01T00:00:00Z'),
    ('Olek', '2025-01-02T00:00:00Z');
INSERT INTO accounts (user_id, balance) VALUES (1, 1000), (2, 500);
INSERT INTO orders (user_id, status, created_at) VALUES
    (1, 'PENDING', '2025-02-01T00:00:00Z'),
    (1, 'PAID', '2025-02-02T00:00:00Z'),
    (2, 'PENDING', '2025-02-03T00:00:00Z');
INSERT INTO order_items (order_id, product_id, quantity) VALUES
    (1, 100, 2), (1, 101, 1), (2, 100, 1), (3, 102, 5);
