package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.service;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command.PlaceOrderCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command.PlaceOrderLineCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.DomainEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.OrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.TransactionManager;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.*;

import java.util.Currency;

// Application service orchestrating the PlaceOrder use case.
// It creates the aggregate, invokes domain behavior, saves it, and publishes events.
public final class PlaceOrderApplicationService {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final TransactionManager transactionManager;

    public PlaceOrderApplicationService(
            OrderRepository orderRepository,
            DomainEventPublisher eventPublisher,
            TransactionManager transactionManager
    ) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
    }

    public OrderId placeOrder(PlaceOrderCommand command) {
        OrderId[] createdOrderId = new OrderId[1];

        transactionManager.executeInTransaction(() -> {
            Currency currency = Currency.getInstance(command.currency());

            Order order = Order.create(
                    OrderId.newId(),
                    CustomerId.of(command.customerId()),
                    currency
            );

            for (PlaceOrderLineCommand line : command.lines()) {
                order.addLine(
                        ProductId.of(line.productId()),
                        line.quantity(),
                        Money.of(line.unitPrice(), currency)
                );
            }

            order.place(Money.of(command.expectedTotal(), currency));

            orderRepository.save(order);

            for (DomainEvent event : order.uncommittedEvents()) {
                eventPublisher.publish(event);
            }

            order.clearEvents();

            createdOrderId[0] = order.id();
        });

        return createdOrderId[0];
    }
}