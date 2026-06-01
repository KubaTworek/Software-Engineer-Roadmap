package pl.jakubtworek.marketplace.inventory.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockItemTest {

    @Test
    void shouldCreateStockItem() {
        UUID productId = UUID.randomUUID();

        StockItem item = StockItem.create(productId, 10);

        assertThat(item.productId()).isEqualTo(productId);
        assertThat(item.availableQuantity()).isEqualTo(10);
        assertThat(item.reservedQuantity()).isZero();
    }

    @Test
    void shouldRejectNegativeInitialQuantity() {
        assertThatThrownBy(() -> StockItem.create(UUID.randomUUID(), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity cannot be negative");
    }

    @Test
    void shouldAddStock() {
        StockItem item = StockItem.create(UUID.randomUUID(), 10);

        item.add(5);

        assertThat(item.availableQuantity()).isEqualTo(15);
    }

    @Test
    void shouldRejectNonPositiveAddedQuantity() {
        StockItem item = StockItem.create(UUID.randomUUID(), 10);

        assertThatThrownBy(() -> item.add(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");
    }

    @Test
    void shouldReserveAvailableStockAndRegisterStockReservedEvent() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();
        StockItem item = StockItem.create(productId, 10);

        boolean result = item.reserve(orderId, 3, correlationId, causationId);

        assertThat(result).isTrue();
        assertThat(item.availableQuantity()).isEqualTo(7);
        assertThat(item.reservedQuantity()).isEqualTo(3);
        assertThat(item.domainEvents()).hasSize(1);
        assertThat(item.domainEvents().getFirst()).isInstanceOf(StockReserved.class);
    }

    @Test
    void shouldFailReservationWhenThereIsNotEnoughStockAndRegisterFailureEvent() {
        UUID orderId = UUID.randomUUID();
        StockItem item = StockItem.create(UUID.randomUUID(), 2);

        boolean result = item.reserve(orderId, 3, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).isFalse();
        assertThat(item.availableQuantity()).isEqualTo(2);
        assertThat(item.reservedQuantity()).isZero();
        assertThat(item.domainEvents()).hasSize(1);
        assertThat(item.domainEvents().getFirst()).isInstanceOf(StockReservationFailed.class);
    }
}
