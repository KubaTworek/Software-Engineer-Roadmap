package pl.jakubtworek.backend_engineering.stage_2.block_a.test.infrastructre;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence.OrderJpaEntity;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence.SpringDataOrderJpaRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// In-memory implementation used only for adapter tests.
// In real integration tests this may be replaced by H2, PostgreSQL Testcontainers, or another database.
public final class InMemorySpringDataOrderJpaRepository implements SpringDataOrderJpaRepository {

    private final Map<String, OrderJpaEntity> entities = new HashMap<>();

    @Override
    public Optional<OrderJpaEntity> findById(String id) {
        return Optional.ofNullable(entities.get(id));
    }

    @Override
    public void save(OrderJpaEntity entity) {
        entities.put(entity.id(), entity);
    }
}