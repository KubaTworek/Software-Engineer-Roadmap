package pl.jakubtworek.cloudarchitecture.repository;

import pl.jakubtworek.cloudarchitecture.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for durable order data. */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {}
