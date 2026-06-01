package pl.jakubtworek.marketplace.payment.domain;

import pl.jakubtworek.marketplace.shared.kernel.AggregateRoot;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.UUID;

public class Payment extends AggregateRoot {
    private final PaymentId id;
    private final UUID orderId;
    private final Money amount;
    private PaymentStatus status;

    private Payment(PaymentId id, UUID orderId, Money amount, PaymentStatus status) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
    }

    public static Payment reserve(UUID orderId, Money amount, boolean accepted, UUID correlationId, UUID causationId) {
        Payment payment = new Payment(PaymentId.newId(), orderId, amount, PaymentStatus.PENDING);
        if (accepted) {
            payment.status = PaymentStatus.RESERVED;
            payment.registerEvent(PaymentReserved.now(payment, correlationId, causationId));
        } else {
            payment.status = PaymentStatus.REJECTED;
            payment.registerEvent(PaymentRejected.now(payment, "Payment gateway rejected reservation", correlationId, causationId));
        }
        return payment;
    }

    public PaymentId id() { return id; }
    public UUID orderId() { return orderId; }
    public Money amount() { return amount; }
    public PaymentStatus status() { return status; }
}
