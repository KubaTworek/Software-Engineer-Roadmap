CREATE TABLE inventory.stock_reservations (
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    released BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (order_id, product_id)
);

CREATE INDEX idx_stock_reservations_active
    ON inventory.stock_reservations(order_id) WHERE released = FALSE;
