package pl.jakubtworek.backend_systems_lab_stage_1.block_c.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository used by authorization logic.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}