package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.out;

import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.OrderId;

import java.util.Optional;

// Output port.
// The application layer depends on this abstraction, not on JPA or SQL.
public interface OrderRepository {

    Optional<Order> findById(OrderId orderId);

    void save(Order order);
}