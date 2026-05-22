package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Order;

// Mapper between the domain aggregate and the persistence model.
// This keeps database structure outside the domain model.
public final class OrderMapper {

    public OrderJpaEntity toEntity(Order order) {
        return new OrderJpaEntity(
                order.id().value(),
                order.customerId().value(),
                order.status().name(),
                order.total().currency().getCurrencyCode(),
                order.total().amount().toPlainString()
        );
    }
}