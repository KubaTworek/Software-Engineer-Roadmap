package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.billing.api.events;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.contracts.MoneyPayload;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration.IntegrationEvent;

import java.time.Instant;

// Event published by the Billing context after payment is completed.
// Sales may consume this event and update the order status.
public final class PaymentCompletedEvent extends IntegrationEvent {

    private final String paymentId;
    private final String orderId;
    private final MoneyPayload paidAmount;

    public PaymentCompletedEvent(
            String eventId,
            Instant occurredAt,
            String paymentId,
            String orderId,
            MoneyPayload paidAmount
    ) {
        super(eventId, "PaymentCompleted", occurredAt);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.paidAmount = paidAmount;
    }

    public String paymentId() {
        return paymentId;
    }

    public String orderId() {
        return orderId;
    }

    public MoneyPayload paidAmount() {
        return paidAmount;
    }
}