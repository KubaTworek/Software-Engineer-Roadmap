package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.repository;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.OrderId;

import java.util.Optional;

// Repository port for the Order aggregate.
// It loads and saves aggregate roots, not database tables.
public interface OrderRepository {

    Optional<Order> findById(OrderId orderId);

    void save(Order order);
}