package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.api.events;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.contracts.MoneyPayload;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.contracts.OrderItemPayload;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration.IntegrationEvent;

import java.time.Instant;
import java.util.List;

// Event published by the Sales context after a customer places an order.
// Downstream contexts such as Billing and Fulfillment react to this event.
public final class OrderPlacedEvent extends IntegrationEvent {

    private final String orderId;
    private final String customerId;
    private final List<OrderItemPayload> items;
    private final MoneyPayload total;

    public OrderPlacedEvent(
            String eventId,
            Instant occurredAt,
            String orderId,
            String customerId,
            List<OrderItemPayload> items,
            MoneyPayload total
    ) {
        super(eventId, "OrderPlaced", occurredAt);
        this.orderId = orderId;
        this.customerId = customerId;
        this.items = List.copyOf(items);
        this.total = total;
    }

    public String orderId() {
        return orderId;
    }

    public String customerId() {
        return customerId;
    }

    public List<OrderItemPayload> items() {
        return items;
    }

    public MoneyPayload total() {
        return total;
    }
}