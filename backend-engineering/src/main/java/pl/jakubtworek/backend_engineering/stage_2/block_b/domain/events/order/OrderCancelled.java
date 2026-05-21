package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.order;

import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.EventMetadata;

/**
 * Event emitted when an order has been cancelled.
 *
 * This event may be produced as a direct user action or as a compensation
 * after a failed payment.
 */
public record OrderCancelled(
        EventMetadata metadata,
        String orderId,
        String reason
) implements DomainEvent {

    public static final String TYPE = "OrderCancelled";
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