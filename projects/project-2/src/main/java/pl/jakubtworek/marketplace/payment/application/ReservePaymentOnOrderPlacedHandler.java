package pl.jakubtworek.marketplace.payment.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.payment.domain.Payment;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

@Component
public class ReservePaymentOnOrderPlacedHandler implements DomainEventHandler<OrderPlaced> {
    private final PaymentGateway gateway;
    private final PaymentRepository repository;
    private final EventPublisher eventPublisher;

    public ReservePaymentOnOrderPlacedHandler(PaymentGateway gateway, PaymentRepository repository, EventPublisher eventPublisher) {
        this.gateway = gateway;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Class<OrderPlaced> eventType() {
        return OrderPlaced.class;
    }

    @Override
    @Transactional
    public void handle(OrderPlaced event) {
        var result = gateway.reserve(event.aggregateId(), event.total());
        var payment = Payment.reserve(event.aggregateId(), event.total(), result.accepted(), event.correlationId(), event.eventId());
        repository.save(payment);
        payment.domainEvents().forEach(eventPublisher::publish);
        payment.clearDomainEvents();
    }
}
