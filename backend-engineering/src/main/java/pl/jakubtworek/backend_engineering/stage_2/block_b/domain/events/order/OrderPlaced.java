package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.order;

import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.EventMetadata;

import java.math.BigDecimal;
import java.util.List;

/**
 * Event emitted when a customer places a new order.
 *
 * This event starts the order processing flow.
 * Consumers should treat it as immutable historical information.
 */
public record OrderPlaced(
        EventMetadata metadata,
        String orderId,
        List<OrderPlacedItem> items,
        BigDecimal totalAmount
) implements DomainEvent {

    public static final String TYPE = "OrderPlaced";
    public static final int VERSION = 1;

    @Override
    public String aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}