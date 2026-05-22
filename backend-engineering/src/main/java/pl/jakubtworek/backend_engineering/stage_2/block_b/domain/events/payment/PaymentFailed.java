package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.payment;

import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.EventMetadata;

/**
 * Event emitted when a payment attempt has failed.
 *
 * This event usually triggers compensation logic, for example cancelling the order.
 */
public record PaymentFailed(
        EventMetadata metadata,
        String orderId,
        String paymentId,
        String reason
) implements DomainEvent {

    public static final String TYPE = "PaymentFailed";
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