package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.events;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.ConsumedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.EventMetadata;

import java.math.BigDecimal;
import java.util.List;

/**
 * Event consumed by Payment Service after an order has been placed.
 *
 * This event may be delivered more than once, so payment processing must be idempotent.
 */
public record OrderPlaced(
        EventMetadata metadata,
        String orderId,
        List<OrderPlacedItem> items,
        BigDecimal totalAmount
) implements ConsumedEvent {

    public static final String TYPE = "OrderPlaced";

    @Override
    public String aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}