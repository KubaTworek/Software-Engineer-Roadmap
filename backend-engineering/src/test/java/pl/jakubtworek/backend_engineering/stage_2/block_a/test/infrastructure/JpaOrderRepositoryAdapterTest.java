package pl.jakubtworek.backend_engineering.stage_2.block_a.test.infrastructure;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.*;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence.JpaOrderRepositoryAdapter;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence.OrderMapper;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence.SpringDataOrderJpaRepository;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

// Integration test for the persistence adapter.
// It verifies mapping and persistence behavior, not domain rules.
class JpaOrderRepositoryAdapterTest {

    @Test
    void shouldSaveOrderUsingPersistenceAdapter() {
        // Given
        SpringDataOrderJpaRepository springDataRepository =
                new InMemorySpringDataOrderJpaRepository();

        JpaOrderRepositoryAdapter adapter = new JpaOrderRepositoryAdapter(
                springDataRepository,
                new OrderMapper()
        );

        Currency currency = Currency.getInstance("PLN");

        Order order = Order.create(
                OrderId.of("O-123"),
                CustomerId.of("C-456"),
                currency
        );

        order.addLine(
                ProductId.of("P-1"),
                2,
                Money.of(new BigDecimal("50.00"), currency)
        );

        order.place(Money.of(new BigDecimal("100.00"), currency));

        // When
        adapter.save(order);

        // Then
        assertTrue(springDataRepository.findById("O-123").isPresent());
    }

    @Test
    void shouldRestoreCompleteAggregateWithoutProducingDomainEvents() {
        SpringDataOrderJpaRepository springDataRepository =
                new InMemorySpringDataOrderJpaRepository();
        JpaOrderRepositoryAdapter adapter = new JpaOrderRepositoryAdapter(
                springDataRepository,
                new OrderMapper()
        );
        Currency currency = Currency.getInstance("PLN");
        Order original = Order.create(
                OrderId.of("O-789"),
                CustomerId.of("C-456"),
                currency
        );
        original.addLine(ProductId.of("P-1"), 2, Money.of(new BigDecimal("12.50"), currency));
        original.addLine(ProductId.of("P-2"), 1, Money.of(new BigDecimal("5.00"), currency));
        original.place(Money.of(new BigDecimal("30.00"), currency));
        adapter.save(original);

        Order restored = adapter.findById(OrderId.of("O-789")).orElseThrow();

        assertEquals(original.id(), restored.id());
        assertEquals(original.customerId(), restored.customerId());
        assertEquals(original.status(), restored.status());
        assertEquals(original.total(), restored.total());
        assertEquals(2, restored.lines().size());
        assertTrue(restored.uncommittedEvents().isEmpty());
    }

    @Test
    void shouldReturnEmptyResultForUnknownOrder() {
        JpaOrderRepositoryAdapter adapter = new JpaOrderRepositoryAdapter(
                new InMemorySpringDataOrderJpaRepository(),
                new OrderMapper()
        );

        assertTrue(adapter.findById(OrderId.of("missing")).isEmpty());
    }
}
