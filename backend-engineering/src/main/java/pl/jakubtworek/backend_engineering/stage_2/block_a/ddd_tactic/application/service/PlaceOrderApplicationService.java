package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.application.service;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.application.command.PlaceOrderCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.application.command.PlaceOrderLineCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.application.port.DomainEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.*;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.repository.OrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.service.CustomerCreditPolicy;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.service.PricingPolicy;

import java.util.Currency;

// Application service orchestrating the Place Order use case.
// It controls the transaction boundary but does not contain business rules.
public final class PlaceOrderApplicationService {

    private final OrderRepository orderRepository;
    private final PricingPolicy pricingPolicy;
    private final CustomerCreditPolicy creditPolicy;
    private final DomainEventPublisher eventPublisher;

    public PlaceOrderApplicationService(
            OrderRepository orderRepository,
            PricingPolicy pricingPolicy,
            CustomerCreditPolicy creditPolicy,
            DomainEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.pricingPolicy = pricingPolicy;
        this.creditPolicy = creditPolicy;
        this.eventPublisher = eventPublisher;
    }

    public OrderId handle(PlaceOrderCommand command) {
        CustomerId customerId = CustomerId.of(command.customerId());
        Currency currency = Currency.getInstance(command.currency());

        Order order = Order.draft(OrderId.newId(), customerId, currency);

        for (PlaceOrderLineCommand line : command.lines()) {
            ProductId productId = ProductId.of(line.productId());
            Quantity quantity = Quantity.of(line.quantity());

            Money unitPrice = pricingPolicy.calculatePrice(productId, quantity);

            order.addLine(productId, quantity, unitPrice);
        }

        if (!creditPolicy.canPlaceOrder(customerId, order.totalPrice())) {
            throw new IllegalStateException("Customer cannot place this order");
        }

        order.place();

        orderRepository.save(order);

        for (DomainEvent event : order.pullDomainEvents()) {
            eventPublisher.publish(event);
        }

        return order.id();
    }
}