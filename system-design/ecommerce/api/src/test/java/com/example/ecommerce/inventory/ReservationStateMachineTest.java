package com.example.ecommerce.inventory;

import com.example.ecommerce.catalog.ProductVariant;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ReservationStateMachineTest {

    @Test
    void terminalReservationCannotBeConfirmedAgain() {
        InventoryReservation reservation = new InventoryReservation(
                10L, mock(ProductVariant.class), 1, Instant.parse("2030-01-01T00:00:00Z"));

        reservation.confirm();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThatThrownBy(reservation::confirm).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(reservation::release).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expirationHasASeparateTerminalState() {
        InventoryReservation reservation = new InventoryReservation(
                10L, mock(ProductVariant.class), 1, Instant.parse("2029-01-01T00:00:00Z"));

        reservation.expire();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }
}
