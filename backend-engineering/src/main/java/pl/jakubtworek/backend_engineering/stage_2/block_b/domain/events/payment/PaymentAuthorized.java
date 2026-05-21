package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.payment;

import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.EventMetadata;

import java.math.BigDecimal;

/**
 * Event emitted when a payment has been successfully authorized.
 *
 * This event can be consumed by Order Service to confirm the order,
 * and by Shipping Service to start fulfillment.
 */
public record PaymentAuthorized(
        EventMetadata metadata,
        String orderId,
        String paymentId,
        BigDecimal amount,
        String status
) implements DomainEvent {

    public static final String TYPE = "PaymentAuthorized";
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