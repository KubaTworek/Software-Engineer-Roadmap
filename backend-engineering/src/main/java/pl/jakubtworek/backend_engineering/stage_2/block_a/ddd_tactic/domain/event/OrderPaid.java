package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.event;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.OrderId;

import java.time.Instant;

// Domain event emitted when an order is marked as paid.
public final class OrderPaid implements DomainEvent {

    private final String eventId;
    private final Instant occurredAt;
    private final OrderId orderId;
    private final String paymentId;

    public OrderPaid(
            String eventId,
            Instant occurredAt,
            OrderId orderId,
            String paymentId
    ) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.orderId = orderId;
        this.paymentId = paymentId;
    }

    @Override
    public String eventId() {
        return eventId;
    }

    @Override
    public Instant occurredAt() {
        return occurredAt;
    }

    public OrderId orderId() {
        return orderId;
    }

    public String paymentId() {
        return paymentId;
    }
}