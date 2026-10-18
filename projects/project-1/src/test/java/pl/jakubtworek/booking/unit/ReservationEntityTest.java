package pl.jakubtworek.booking.unit;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.booking.entity.Customer;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.entity.ReservationStatus;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationEntityTest {
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Test
    void paymentTimeoutIsAcceptedBeforeDeadlineAndRejectedAtDeadline() {
        Reservation beforeDeadline = reservation();
        Reservation atDeadline = reservation();

        beforeDeadline.markPaymentTimeout(NOW.plus(Duration.ofMinutes(14)));
        assertThat(beforeDeadline.getStatus()).isEqualTo(ReservationStatus.PAYMENT_TIMEOUT);

        assertThatThrownBy(() -> atDeadline.markPaymentTimeout(NOW.plus(Duration.ofMinutes(15))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expired");
        assertThat(atDeadline.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void newReservationStartsAsPending() {
        // given & when
        Reservation reservation = reservation();

        // then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getCreatedAt()).isNotNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getCancelledAt()).isNull();
    }

    @Test
    void confirmsPendingReservation() {
        // given
        Reservation reservation = reservation();

        // when
        reservation.confirm(NOW.plusSeconds(1));

        // then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isNotNull();
    }

    @Test
    void cancelsPendingReservationOnlyOnce() {
        // given
        Reservation reservation = reservation();

        // when
        boolean firstCancel = reservation.cancel(NOW.plusSeconds(1));
        boolean secondCancel = reservation.cancel(NOW.plusSeconds(2));

        // then
        assertThat(firstCancel).isTrue();
        assertThat(secondCancel).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCancelledAt()).isNotNull();
    }

    @Test
    void rejectsCancellingConfirmedReservation() {
        // given
        Reservation reservation = reservation();
        reservation.confirm(NOW.plusSeconds(1));

        // when & then
        assertThatThrownBy(() -> reservation.cancel(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Confirmed reservation cannot be cancelled");
    }

    @Test
    void rejectsConfirmingCancelledReservation() {
        // given
        Reservation reservation = reservation();
        reservation.cancel(NOW.plusSeconds(1));

        // when & then
        assertThatThrownBy(() -> reservation.confirm(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only pending reservation can be confirmed");
    }

    @Test
    void expiresOnlyAfterDeadlineAndOnlyOnce() {
        Reservation reservation = reservation();

        assertThat(reservation.expire(NOW.plus(Duration.ofMinutes(14)))).isFalse();
        assertThat(reservation.expire(NOW.plus(Duration.ofMinutes(15)))).isTrue();
        assertThat(reservation.expire(NOW.plus(Duration.ofMinutes(16)))).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void rejectsConfirmationAtExpirationBoundary() {
        Reservation reservation = reservation();

        assertThatThrownBy(() -> reservation.confirm(NOW.plus(Duration.ofMinutes(15))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expired reservation");
    }

    @Test
    void cancellingAlreadyExpiredReservationDoesNotReleaseCapacityAgain() {
        Reservation reservation = reservation();
        reservation.expire(NOW.plus(Duration.ofMinutes(15)));

        assertThat(reservation.cancel(NOW.plus(Duration.ofMinutes(16)))).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    private Reservation reservation() {
        Event event = new Event(
                "Unit Test Event",
                "Warsaw",
                "education",
                OffsetDateTime.now().plusDays(7)
        );
        Customer customer = new Customer("Unit Tester", "unit.tester@example.com");
        return new Reservation(event, customer, NOW, Duration.ofMinutes(15));
    }
}
