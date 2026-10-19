package pl.jakubtworek.cloudarchitecture.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.jakubtworek.cloudarchitecture.dto.CreateOrderRequest;
import pl.jakubtworek.cloudarchitecture.dto.OrderCreatedResponse;
import pl.jakubtworek.cloudarchitecture.entity.OrderEntity;
import pl.jakubtworek.cloudarchitecture.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.cloudarchitecture.entity.OutboxEventEntity;
import pl.jakubtworek.cloudarchitecture.repository.OutboxEventRepository;

import java.util.Objects;
import java.time.Clock;
import java.time.Instant;

/**
 * Service responsible for order creation.
 *
 * The synchronous part stores the order and records an outbox event. Heavy work
 * should be performed asynchronously by workers subscribed to Pub/Sub.
 */
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OrderService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Creates an order and records a domain event for later publication.
     *
     * The API does not wait for slow downstream processing. This keeps request
     * latency lower and makes the system more loosely coupled.
     */
    @Transactional
    public OrderCreatedResponse createOrder(CreateOrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant createdAt = Instant.now(clock);
        OrderEntity order = orderRepository.save(new OrderEntity(request.customerId(), createdAt));
        Long orderId = Objects.requireNonNull(order.getId(), "saved order must have an id");
        outboxRepository.save(new OutboxEventEntity(
                "ORDER",
                orderId,
                "ORDER_CREATED",
                serialize(new OrderCreatedEvent(orderId)),
                createdAt
        ));
        return new OrderCreatedResponse(orderId, "ACCEPTED");
    }

    private String serialize(OrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            // Throwing here rolls back both the order and outbox record.
            throw new IllegalStateException("could not serialize ORDER_CREATED event", exception);
        }
    }

    private record OrderCreatedEvent(Long orderId) {
    }
}
