package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence;

import java.util.Optional;

// Low-level database repository.
// In a real Spring application, this may be a Spring Data repository.
public interface SpringDataOrderJpaRepository {

    Optional<OrderJpaEntity> findById(String id);

    void save(OrderJpaEntity entity);
}