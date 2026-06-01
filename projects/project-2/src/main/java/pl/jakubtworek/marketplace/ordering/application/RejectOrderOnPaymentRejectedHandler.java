package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.payment.domain.PaymentRejected;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class RejectOrderOnPaymentRejectedHandler implements DomainEventHandler<PaymentRejected> {
    private final OrderRepository repository;

    public RejectOrderOnPaymentRejectedHandler(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<PaymentRejected> eventType() {
        return PaymentRejected.class;
    }

    @Override
    @Transactional
    public void handle(PaymentRejected event) {
        var order = repository.findById(OrderId.of(event.orderId())).orElseThrow();
        order.reject(event.reason());
        repository.save(order);
        order.clearDomainEvents();
    }
}
