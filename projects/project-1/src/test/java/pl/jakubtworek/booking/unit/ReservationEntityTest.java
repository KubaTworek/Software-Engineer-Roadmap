package pl.jakubtworek.booking.unit;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.booking.entity.Customer;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.entity.ReservationStatus;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationEntityTest {
    @Test
    void newReservationStartsAsPending() {
        Reservation reservation = reservation();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getCreatedAt()).isNotNull();
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getCancelledAt()).isNull();
    }

    @Test
    void confirmsPendingReservation() {
        Reservation reservation = reservation();

        reservation.confirm();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isNotNull();
    }

    @Test
    void cancelsPendingReservationOnlyOnce() {
        Reservation reservation = reservation();

        boolean firstCancel = reservation.cancel();
        boolean secondCancel = reservation.cancel();

        assertThat(firstCancel).isTrue();
        assertThat(secondCancel).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCancelledAt()).isNotNull();
    }

    @Test
    void rejectsCancellingConfirmedReservation() {
        Reservation reservation = reservation();
        reservation.confirm();

        assertThatThrownBy(reservation::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Confirmed reservation cannot be cancelled");
    }

    @Test
    void rejectsConfirmingCancelledReservation() {
        Reservation reservation = reservation();
        reservation.cancel();

        assertThatThrownBy(reservation::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only pending reservation can be confirmed");
    }

    private Reservation reservation() {
        Event event = new Event(
                "Unit Test Event",
                "Warsaw",
                "education",
                OffsetDateTime.now().plusDays(7)
        );
        Customer customer = new Customer("Unit Tester", "unit.tester@example.com");
        return new Reservation(event, customer);
    }
}
