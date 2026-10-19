package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.CustomerId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Money;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderLine;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderStatus;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.ProductId;

import java.math.BigDecimal;
import java.util.Currency;

// Mapper between the domain aggregate and the persistence model.
// This keeps database structure outside the domain model.
public final class OrderMapper {

    public OrderJpaEntity toEntity(Order order) {
        return new OrderJpaEntity(
                order.id().value(),
                order.customerId().value(),
                order.status().name(),
                order.total().currency().getCurrencyCode(),
                order.total().amount().toPlainString(),
                order.lines().stream()
                        .map(line -> new OrderLineJpaEntity(
                                line.productId().value(),
                                line.quantity(),
                                line.unitPrice().amount().toPlainString()
                        ))
                        .toList()
        );
    }

    public Order toDomain(OrderJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.currency());
        var lines = entity.lines().stream()
                .map(line -> new OrderLine(
                        ProductId.of(line.productId()),
                        line.quantity(),
                        Money.of(new BigDecimal(line.unitPrice()), currency)
                ))
                .toList();

        return Order.restore(
                OrderId.of(entity.id()),
                CustomerId.of(entity.customerId()),
                lines,
                OrderStatus.valueOf(entity.status()),
                Money.of(new BigDecimal(entity.totalAmount()), currency)
        );
    }
}
