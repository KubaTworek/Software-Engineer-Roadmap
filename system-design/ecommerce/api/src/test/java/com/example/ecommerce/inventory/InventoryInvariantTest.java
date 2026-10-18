package com.example.ecommerce.inventory;

import com.example.ecommerce.catalog.ProductVariant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class InventoryInvariantTest {

    @Test
    void reservationCannotOversellStock() {
        InventoryItem item = new InventoryItem(mock(ProductVariant.class), 3);

        item.reserve(2);

        assertThat(item.sellableQuantity()).isEqualTo(1);
        assertThatThrownBy(() -> item.reserve(2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oversell");
    }

    @Test
    void saleRequiresAndConsumesExistingReservation() {
        InventoryItem item = new InventoryItem(mock(ProductVariant.class), 5);
        item.reserve(2);

        item.confirmSale(2);

        assertThat(item.getAvailableQuantity()).isEqualTo(3);
        assertThat(item.getReservedQuantity()).isZero();
        assertThat(item.getSoldQuantity()).isEqualTo(2);
        assertThatThrownBy(() -> item.confirmSale(1))
                .isInstanceOf(IllegalStateException.class);
    }
}
