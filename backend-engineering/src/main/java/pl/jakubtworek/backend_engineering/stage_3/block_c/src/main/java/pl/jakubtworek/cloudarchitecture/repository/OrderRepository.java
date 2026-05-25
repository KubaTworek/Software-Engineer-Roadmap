package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.repository;

import com.example.cloudarchitecture.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for durable order data. */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {}
