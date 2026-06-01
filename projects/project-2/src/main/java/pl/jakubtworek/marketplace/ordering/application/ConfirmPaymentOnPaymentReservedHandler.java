package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.payment.domain.PaymentReserved;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

@Component
public class ConfirmPaymentOnPaymentReservedHandler implements DomainEventHandler<PaymentReserved> {
    private final OrderRepository repository;
    private final EventPublisher eventPublisher;

    public ConfirmPaymentOnPaymentReservedHandler(OrderRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override public Class<PaymentReserved> eventType() { return PaymentReserved.class; }

    @Override
    @Transactional
    public void handle(PaymentReserved event) {
        var order = repository.findById(OrderId.of(event.orderId())).orElseThrow();
        order.markPaymentReserved(event.correlationId(), event.eventId());
        repository.save(order);
        var events = java.util.List.copyOf(order.domainEvents());
        order.clearDomainEvents();
        events.forEach(eventPublisher::publish);
    }
}
