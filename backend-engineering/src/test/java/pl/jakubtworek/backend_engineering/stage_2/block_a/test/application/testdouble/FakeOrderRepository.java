package pl.jakubtworek.backend_engineering.stage_2.block_a.test.application.testdouble;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.OrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Test double for OrderRepository.
// It allows application service tests to run without a real database.
public final class FakeOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> orders = new HashMap<>();
    private Order lastSavedOrder;
    private int saveCount;

    @Override
    public Optional<Order> findById(OrderId id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public void save(Order order) {
        orders.put(order.id(), order);
        lastSavedOrder = order;
        saveCount++;
    }

    public Order lastSavedOrder() {
        return lastSavedOrder;
    }

    public int saveCount() {
        return saveCount;
    }
}