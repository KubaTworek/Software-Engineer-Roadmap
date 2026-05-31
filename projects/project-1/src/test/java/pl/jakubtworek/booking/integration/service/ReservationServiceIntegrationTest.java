package pl.jakubtworek.booking.integration.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.ReservationStatus;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.service.EventService;
import pl.jakubtworek.booking.service.ReservationService;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM outbound_messages",
        "DELETE FROM audit_logs",
        "DELETE FROM reservations",
        "DELETE FROM capacity_pools",
        "DELETE FROM refresh_tokens",
        "DELETE FROM app_users",
        "DELETE FROM events",
        "DELETE FROM customers",
        "DELETE FROM organizations"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationServiceIntegrationTest {
    @Autowired
    EventService eventService;

    @Autowired
    ReservationService reservationService;

    @Test
    void createsPendingReservationAndDecreasesAvailableCapacity() {
        // given
        EventResponse event = createEvent(2);

        // when
        ReservationResponse reservation = reservationService.create(event.id(), reservationRequest("jan.kowalski@example.com"));

        // then
        assertThat(reservation.id()).isNotNull();
        assertThat(reservation.eventId()).isEqualTo(event.id());
        assertThat(reservation.customerEmail()).isEqualTo("jan.kowalski@example.com");
        assertThat(reservation.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.confirmedAt()).isNull();
        assertThat(reservation.cancelledAt()).isNull();
        assertThat(eventService.get(event.id()).availableCapacity()).isEqualTo(1);
    }

    @Test
    void confirmsPendingReservation() {
        // given
        EventResponse event = createEvent(1);
        ReservationResponse created = reservationService.create(event.id(), reservationRequest("anna.nowak@example.com"));

        // when
        ReservationResponse confirmed = reservationService.confirm(created.id());

        // then
        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.confirmedAt()).isNotNull();
        assertThat(confirmed.cancelledAt()).isNull();
        assertThat(eventService.get(event.id()).availableCapacity()).isZero();
    }

    @Test
    void cancelsPendingReservationAndReleasesCapacity() {
        // given
        EventResponse event = createEvent(1);
        ReservationResponse created = reservationService.create(event.id(), reservationRequest("ewa.zielinska@example.com"));

        // when
        ReservationResponse cancelled = reservationService.cancel(created.id());

        // then
        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(eventService.get(event.id()).availableCapacity()).isEqualTo(1);
    }

    @Test
    void doesNotReleaseCapacityTwiceWhenReservationIsCancelledTwice() {
        // given
        EventResponse event = createEvent(1);
        ReservationResponse created = reservationService.create(event.id(), reservationRequest("adam.test@example.com"));

        // when
        reservationService.cancel(created.id());
        reservationService.cancel(created.id());

        // then
        EventResponse afterSecondCancel = eventService.get(event.id());
        assertThat(afterSecondCancel.availableCapacity()).isEqualTo(1);
        assertThat(afterSecondCancel.totalCapacity()).isEqualTo(1);
    }

    @Test
    void preventsOversellingInSequentialMvpFlow() {
        // given
        EventResponse event = createEvent(1);
        reservationService.create(event.id(), reservationRequest("first@example.com"));

        // when & then
        assertThatThrownBy(() -> reservationService.create(event.id(), reservationRequest("second@example.com")))
                .isInstanceOf(CapacityUnavailableException.class)
                .hasMessageContaining("No available capacity");

        assertThat(eventService.get(event.id()).availableCapacity()).isZero();
    }

    @Test
    void throwsNotFoundWhenCreatingReservationForMissingEvent() {
        // given
        UUID missingEventId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> reservationService.create(missingEventId, reservationRequest("missing@example.com")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Event not found");
    }

    @Test
    void doesNotAllowCancellingConfirmedReservationInBaseMvp() {
        // given
        EventResponse event = createEvent(1);
        ReservationResponse created = reservationService.create(event.id(), reservationRequest("confirmed@example.com"));
        reservationService.confirm(created.id());

        // when & then
        assertThatThrownBy(() -> reservationService.cancel(created.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Confirmed reservation cannot be cancelled");
    }

    @Test
    void doesNotAllowConfirmingCancelledReservation() {
        // given
        EventResponse event = createEvent(1);
        ReservationResponse created = reservationService.create(event.id(), reservationRequest("cancelled@example.com"));
        reservationService.cancel(created.id());

        // when & then
        assertThatThrownBy(() -> reservationService.confirm(created.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only pending reservation can be confirmed");
    }

    private EventResponse createEvent(int capacity) {
        return eventService.create(new EventCreateRequest(
                "Backend Engineering Workshop",
                "Warsaw",
                "education",
                OffsetDateTime.now().plusDays(30),
                capacity
        ));
    }

    private ReservationCreateRequest reservationRequest(String email) {
        return new ReservationCreateRequest("Test User", email);
    }
}
