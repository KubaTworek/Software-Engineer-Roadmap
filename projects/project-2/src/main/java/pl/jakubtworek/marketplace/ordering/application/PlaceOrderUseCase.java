package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.ordering.domain.*;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.List;
import java.util.UUID;

@Service
public class PlaceOrderUseCase {
    private final OrderRepository repository;
    private final EventPublisher eventPublisher;

    public PlaceOrderUseCase(OrderRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderId handle(Command command) {
        List<OrderLine> lines = command.lines().stream()
                .map(line -> new OrderLine(ProductId.of(line.productId()), line.quantity(), Money.of(line.unitAmount(), line.currency())))
                .toList();
        Order order = Order.place(CustomerId.of(command.customerId()), lines, command.correlationId());
        repository.save(order);
        order.domainEvents().forEach(eventPublisher::publish);
        order.clearDomainEvents();
        return order.id();
    }

    public record Command(UUID customerId, List<Line> lines, UUID correlationId) {}
    public record Line(UUID productId, int quantity, String unitAmount, String currency) {}
}
