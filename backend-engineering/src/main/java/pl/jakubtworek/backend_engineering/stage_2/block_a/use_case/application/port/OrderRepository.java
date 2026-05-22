package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderId;

import java.util.Optional;

// Repository port for the Order aggregate.
// The application depends on this abstraction, not on JPA, SQL, or Hibernate.
public interface OrderRepository {

    Optional<Order> findById(OrderId id);

    void save(Order order);
}