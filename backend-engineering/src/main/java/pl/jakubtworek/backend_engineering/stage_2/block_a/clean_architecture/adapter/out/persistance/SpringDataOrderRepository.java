package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.persistance;

import java.util.Optional;

// Low-level persistence abstraction.
// In a real application this may extend Spring Data JpaRepository.
public interface SpringDataOrderRepository {

    Optional<OrderJpaEntity> findById(String id);

    OrderJpaEntity save(OrderJpaEntity entity);
}