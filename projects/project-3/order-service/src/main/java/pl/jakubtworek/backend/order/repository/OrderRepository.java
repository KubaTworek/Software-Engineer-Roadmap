package pl.jakubtworek.backend.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.jakubtworek.backend.order.domain.OrderEntity;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
