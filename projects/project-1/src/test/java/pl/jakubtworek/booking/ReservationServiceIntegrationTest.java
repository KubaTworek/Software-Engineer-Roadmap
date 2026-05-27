package pl.jakubtworek.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.ReservationStatus;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.service.EventService;
import pl.jakubtworek.booking.service.ReservationService;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceIntegrationTest {
    @Autowired
    EventService eventService;

    @Autowired
    ReservationService reservationService;

    @Test
    void createsReservationAndPreventsOverselling() {
        EventResponse event = eventService.create(new EventCreateRequest(
                "Small Workshop",
                "Warsaw",
                "education",
                OffsetDateTime.now().plusDays(30),
                1
        ));

        ReservationResponse reservation = reservationService.create(event.id(), new ReservationCreateRequest(
                "Jan Kowalski",
                "jan.kowalski@example.com"
        ));

        assertThat(reservation.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(eventService.get(event.id()).availableCapacity()).isZero();

        assertThatThrownBy(() -> reservationService.create(event.id(), new ReservationCreateRequest(
                "Anna Nowak",
                "anna.nowak@example.com"
        ))).isInstanceOf(CapacityUnavailableException.class);
    }
}
