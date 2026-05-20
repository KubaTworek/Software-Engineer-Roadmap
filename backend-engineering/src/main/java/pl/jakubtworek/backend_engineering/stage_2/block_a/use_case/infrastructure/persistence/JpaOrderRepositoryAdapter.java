package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.OrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderId;

import java.util.Optional;

// Persistence adapter implementing the OrderRepository port.
// The application layer depends on the port, not on this adapter.
public final class JpaOrderRepositoryAdapter implements OrderRepository {

    private final SpringDataOrderJpaRepository jpaRepository;
    private final OrderMapper mapper;

    public JpaOrderRepositoryAdapter(
            SpringDataOrderJpaRepository jpaRepository,
            OrderMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        throw new UnsupportedOperationException(
                "Mapping from persistence entity to full aggregate omitted for brevity"
        );
    }

    @Override
    public void save(Order order) {
        jpaRepository.save(mapper.toEntity(order));
    }
}