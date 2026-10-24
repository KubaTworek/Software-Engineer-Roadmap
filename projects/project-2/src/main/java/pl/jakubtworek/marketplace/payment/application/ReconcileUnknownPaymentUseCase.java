package pl.jakubtworek.marketplace.payment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.payment.domain.PaymentStatus;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

import java.util.UUID;

/** Production boundary dla niepewnego wyniku: lookup, nigdy drugi reserve. */
@Service
public class ReconcileUnknownPaymentUseCase {
    private final PaymentGateway gateway;
    private final PaymentRepository payments;
    private final EventPublisher events;

    public ReconcileUnknownPaymentUseCase(PaymentGateway gateway, PaymentRepository payments, EventPublisher events) {
        this.gateway = gateway; this.payments = payments; this.events = events;
    }

    @Transactional
    public PaymentStatus reconcile(UUID orderId, UUID correlationId) {
        var payment = payments.findByOrderId(orderId).orElseThrow();
        if (payment.status() != PaymentStatus.UNKNOWN) return payment.status();
        var result = gateway.lookup(payment.providerOperationId());
        if (result.outcome() == PaymentGateway.Outcome.UNKNOWN) return PaymentStatus.UNKNOWN;
        payment.resolve(result.accepted(), correlationId, UUID.randomUUID(), result.reason());
        if (!payments.updateIfStatus(payment, PaymentStatus.UNKNOWN)) {
            // Inna replika rozstrzygnęła stan podczas I/O. Nie emitujemy drugiego faktu.
            return payments.findByOrderId(orderId).orElseThrow().status();
        }
        payment.domainEvents().forEach(events::publish);
        payment.clearDomainEvents();
        return payment.status();
    }
}
