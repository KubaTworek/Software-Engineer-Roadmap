CREATE TABLE integration.order_fulfillment_processes (
    order_id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    completed_steps TEXT[] NOT NULL DEFAULT '{}',
    deadline_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT order_fulfillment_process_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPENSATING', 'CANCELLED')),
    CONSTRAINT order_fulfillment_process_version CHECK (version > 0)
);

CREATE INDEX idx_order_fulfillment_process_deadline
    ON integration.order_fulfillment_processes(status, deadline_at);

COMMENT ON TABLE integration.order_fulfillment_processes IS
    'Durable saga snapshot. Optimistic version prevents silently losing concurrent payment/stock facts.';
