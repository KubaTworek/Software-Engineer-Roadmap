package pl.jakubtworek.marketplace.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.jakubtworek.marketplace.integration.contracts.OrderPlacedIntegrationEvent;
import pl.jakubtworek.marketplace.inventory.application.ReserveStockOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.ordering.api.IdempotencyKeyRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DurableBoundariesIntegrationTest extends AbstractIntegrationTest {
    @Autowired IdempotencyKeyRegistry idempotency;
    @Autowired ReserveStockOnOrderPlacedHandler stockHandler;

    @Test void sameIdempotencyKeyAcrossConcurrentReplicasExecutesCommandOnce() throws Exception {
        var calls = new AtomicInteger();
        UUID result = UUID.randomUUID();
        String fingerprint = "a".repeat(64);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> idempotency.execute("checkout-42", fingerprint, () -> {
                calls.incrementAndGet(); return result;
            }));
            var second = pool.submit(() -> idempotency.execute("checkout-42", fingerprint, () -> {
                calls.incrementAndGet(); return result;
            }));
            assertThat(List.of(first.get().value(), second.get().value())).containsOnly(result);
        }
        assertThat(calls).hasValue(1);
        assertThat(countRows("ordering", "api_idempotency_keys")).isEqualTo(1);
    }

    @Test void concurrentReservationsCannotOversellOneStockRow() throws Exception {
        UUID productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO inventory.stock_items(product_id, available_quantity, reserved_quantity) VALUES (?, 10, 0)", productId);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> stockHandler.handle(orderPlaced(productId, 7)));
            var two = pool.submit(() -> stockHandler.handle(orderPlaced(productId, 7)));
            one.get(); two.get();
        }

        var quantities = jdbcTemplate.queryForMap(
                "SELECT available_quantity, reserved_quantity FROM inventory.stock_items WHERE product_id = ?", productId);
        assertThat(quantities).containsEntry("available_quantity", 3).containsEntry("reserved_quantity", 7);
    }

    private OrderPlacedIntegrationEvent orderPlaced(UUID productId, int quantity) {
        UUID eventId = UUID.randomUUID();
        var money = new OrderPlacedIntegrationEvent.MoneyValue(BigDecimal.TEN, "PLN");
        return new OrderPlacedIntegrationEvent(eventId, UUID.randomUUID(), UUID.randomUUID(), money,
                List.of(new OrderPlacedIntegrationEvent.Line(productId, quantity, money)), Instant.now(), UUID.randomUUID(), null);
    }
}
