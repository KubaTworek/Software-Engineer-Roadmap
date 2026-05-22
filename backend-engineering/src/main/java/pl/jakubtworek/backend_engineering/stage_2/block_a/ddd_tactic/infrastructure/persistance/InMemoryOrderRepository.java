package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.infrastructure.persistance;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.OrderId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.repository.OrderRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Simple in-memory repository adapter.
// In production, this could be replaced by JPA, JDBC, or another persistence mechanism.
public final class InMemoryOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> orders = new HashMap<>();

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public void save(Order order) {
        orders.put(order.id(), order);
    }
}