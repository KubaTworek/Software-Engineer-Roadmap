package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event;

import java.time.Instant;

// Event published by Billing when payment fails.
// It may trigger compensation steps in other contexts.
public record PaymentFailedEvent(
        String eventId,
        String orderId,
        String paymentId,
        String reason,
        Instant occurredAt
) implements IntegrationEvent {

    @Override
    public String eventType() {
        return "PaymentFailed";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}