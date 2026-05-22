package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.persistance;

import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.out.OrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.OrderId;

import java.util.Optional;

// Outbound persistence adapter.
// It implements the application port using a database-specific mechanism.
public final class JpaOrderRepositoryAdapter implements OrderRepository {

    private final SpringDataOrderRepository springDataRepository;
    private final OrderPersistenceMapper mapper;

    public JpaOrderRepositoryAdapter(
            SpringDataOrderRepository springDataRepository,
            OrderPersistenceMapper mapper
    ) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return springDataRepository.findById(orderId.value())
                .map(mapper::toDomain);
    }

    @Override
    public void save(Order order) {
        OrderJpaEntity entity = mapper.toEntity(order);
        springDataRepository.save(entity);
    }
}