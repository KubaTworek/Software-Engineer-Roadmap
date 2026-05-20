package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.event;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.*;

import java.time.Instant;
import java.util.List;

// Domain event emitted when an order is placed.
// It describes a fact that already happened inside the Sales context.
public final class OrderPlaced implements DomainEvent {

    private final String eventId;
    private final Instant occurredAt;
    private final OrderId orderId;
    private final CustomerId customerId;
    private final List<OrderLine> lines;
    private final Money totalPrice;

    public OrderPlaced(
            String eventId,
            Instant occurredAt,
            OrderId orderId,
            CustomerId customerId,
            List<OrderLine> lines,
            Money totalPrice
    ) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.orderId = orderId;
        this.customerId = customerId;
        this.lines = List.copyOf(lines);
        this.totalPrice = totalPrice;
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

    public CustomerId customerId() {
        return customerId;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public Money totalPrice() {
        return totalPrice;
    }
}