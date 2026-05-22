package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event;

import java.math.BigDecimal;
import java.time.Instant;

// Event published by Billing after successful payment.
// Other contexts may use it to continue the order process.
public record PaymentCompletedEvent(
        String eventId,
        String orderId,
        String paymentId,
        BigDecimal paidAmount,
        String currency,
        Instant occurredAt
) implements IntegrationEvent {

    @Override
    public String eventType() {
        return "PaymentCompleted";
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