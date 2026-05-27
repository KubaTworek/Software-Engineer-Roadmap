package pl.jakubtworek.booking.integration;

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
        "DELETE FROM reservations",
        "DELETE FROM capacity_pools",
        "DELETE FROM app_users",
        "DELETE FROM organizations",
        "DELETE FROM customers",
        "DELETE FROM events"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationServiceIntegrationTest {
    @Autowired
    EventService eventService;

    @Autowired
    ReservationService reservationService;

    @Test
    void createsPendingReservationAndDecreasesAvailableCapacity() {
        EventResponse event = createEvent(2);

        ReservationResponse reservation = reservationService.create(event.id(), reservationRequest("jan.kowalski@example.com"));

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
        EventResponse event = createEvent(1);
        ReservationResponse created = reservationService.create(event.id(), reservationRequest("anna.nowak@example.com"));

        ReservationResponse confirmed = reservationService.confirm(created.id());

        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.confirmedAt()).isNotNull();
        assertThat(confirmed.cancelledAt()).isNull();
        assertThat(eventService.get(event.id()).availableCapacity()).isZero();
    }

    @Test
    void cancelsPendingReservationAndReleasesCapacity() {
        EventResponse event = createEvent(1);
        ReservationResponse created = reservationService.create(event.id(), reservationRequest("ewa.zielinska@example.com"));

        ReservationResponse cancelled = reservationService.cancel(created.id());

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(eventService.get(event.id()).availableCapacity()).isEqualTo(1);
    }

    @Test
    void doesNotReleaseCapacityTwiceWhenReservationIsCancelledTwice() {
        EventResponse event = createEvent(1);
        ReservationResponse created = reservationService.create(event.id(), reservationRequest("adam.test@example.com"));

        reservationService.cancel(created.id());
        reservationService.cancel(created.id());

        EventResponse afterSecondCancel = eventService.get(event.id());
        assertThat(afterSecondCancel.availableCapacity()).isEqualTo(1);
        assertThat(afterSecondCancel.totalCapacity()).isEqualTo(1);
    }

    @Test
    void preventsOversellingInSequentialMvpFlow() {
        EventResponse event = createEvent(1);
        reservationService.create(event.id(), reservationRequest("first@example.com"));

        assertThatThrownBy(() -> reservationService.create(event.id(), reservationRequest("second@example.com")))
                .isInstanceOf(CapacityUnavailableException.class)
                .hasMessageContaining("No available capacity");

        assertThat(eventService.get(event.id()).availableCapacity()).isZero();
    }

    @Test
    void throwsNotFoundWhenCreatingReservationForMissingEvent() {
        UUID missingEventId = UUID.randomUUID();

        assertThatThrownBy(() -> reservationService.create(missingEventId, reservationRequest("missing@example.com")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Event not found");
    }

    @Test
    void doesNotAllowCancellingConfirmedReservationInBaseMvp() {
        EventResponse event = createEvent(1);
        ReservationResponse created = reservationService.create(event.id(), reservationRequest("confirmed@example.com"));
        reservationService.confirm(created.id());

        assertThatThrownBy(() -> reservationService.cancel(created.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Confirmed reservation cannot be cancelled");
    }

    @Test
    void doesNotAllowConfirmingCancelledReservation() {
        EventResponse event = createEvent(1);
        ReservationResponse created = reservationService.create(event.id(), reservationRequest("cancelled@example.com"));
        reservationService.cancel(created.id());

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
