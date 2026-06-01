package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

import java.util.List;
import java.util.UUID;

@Service
public class CancelOrderUseCase {
    private final OrderRepository repository;
    private final EventPublisher eventPublisher;

    public CancelOrderUseCase(OrderRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(UUID orderId, UUID correlationId) {
        var order = repository.findById(OrderId.of(orderId)).orElseThrow();
        order.cancel(correlationId, null);
        repository.save(order);
        var events = List.copyOf(order.domainEvents());
        order.clearDomainEvents();
        events.forEach(eventPublisher::publish);
    }
}
