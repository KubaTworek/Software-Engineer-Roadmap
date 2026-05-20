package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.application.service;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.application.port.DomainEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.OrderId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.repository.OrderRepository;

// Application service reacting to payment confirmation.
// It loads one aggregate, invokes domain behavior, saves it, and publishes events.
public final class MarkOrderAsPaidApplicationService {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    public MarkOrderAsPaidApplicationService(
            OrderRepository orderRepository,
            DomainEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    public void handle(String orderId, String paymentId) {
        Order order = orderRepository.findById(OrderId.of(orderId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        order.markAsPaid(paymentId);

        orderRepository.save(order);

        for (DomainEvent event : order.pullDomainEvents()) {
            eventPublisher.publish(event);
        }
    }
}