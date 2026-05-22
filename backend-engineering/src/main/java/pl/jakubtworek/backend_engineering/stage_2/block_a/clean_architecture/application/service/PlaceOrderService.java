package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.service;

import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in.PlaceOrderCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in.PlaceOrderResult;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in.PlaceOrderUseCase;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.out.OrderEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.out.OrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.event.OrderPlaced;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.CustomerId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

// Use case implementation.
// It orchestrates the business flow but does not contain infrastructure code.
public final class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public PlaceOrderService(
            OrderRepository orderRepository,
            OrderEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
        OrderId orderId = new OrderId("O-" + UUID.randomUUID());
        CustomerId customerId = new CustomerId(command.customerId());

        Order order = Order.create(orderId, customerId);

        order.place();

        orderRepository.save(order);

        eventPublisher.publish(new OrderPlaced(
                UUID.randomUUID().toString(),
                order.id(),
                Instant.now()
        ));

        return new PlaceOrderResult(
                order.id().value(),
                order.status().name()
        );
    }
}