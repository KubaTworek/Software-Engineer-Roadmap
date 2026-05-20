package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.persistance;

import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.CustomerId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.OrderId;

// Mapper between persistence model and domain model.
// Mapping protects the domain from JPA-specific structure.
public final class OrderPersistenceMapper {

    public OrderJpaEntity toEntity(Order order) {
        return new OrderJpaEntity(
                order.id().value(),
                order.customerId().value(),
                order.status().name()
        );
    }

    public Order toDomain(OrderJpaEntity entity) {
        Order order = Order.create(
                new OrderId(entity.id()),
                new CustomerId(entity.customerId())
        );

        if ("PLACED".equals(entity.status())) {
            order.place();
        }

        return order;
    }
}