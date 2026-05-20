package pl.jakubtworek.backend_engineering.stage_2.block_a.test.infrastructre;

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
}