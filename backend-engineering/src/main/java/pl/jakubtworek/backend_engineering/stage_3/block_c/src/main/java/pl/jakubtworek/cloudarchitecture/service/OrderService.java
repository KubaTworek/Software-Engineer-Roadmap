package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service;

import com.example.cloudarchitecture.dto.CreateOrderRequest;
import com.example.cloudarchitecture.dto.OrderCreatedResponse;
import com.example.cloudarchitecture.entity.OrderEntity;
import com.example.cloudarchitecture.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for order creation.
 *
 * The synchronous part stores the order and publishes an event. Heavy work
 * should be performed asynchronously by workers subscribed to Pub/Sub.
 */
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PubSubPublisher pubSubPublisher;

    public OrderService(OrderRepository orderRepository, PubSubPublisher pubSubPublisher) {
        this.orderRepository = orderRepository;
        this.pubSubPublisher = pubSubPublisher;
    }

    /**
     * Creates an order and publishes a domain event.
     *
     * The API does not wait for slow downstream processing. This keeps request
     * latency lower and makes the system more loosely coupled.
     */
    @Transactional
    public OrderCreatedResponse createOrder(CreateOrderRequest request) {
        OrderEntity order = orderRepository.save(new OrderEntity(request.customerId()));
        pubSubPublisher.publishOrderCreated(order.getId());
        return new OrderCreatedResponse(order.getId(), "ACCEPTED");
    }
}
