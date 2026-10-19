package pl.jakubtworek.booking.unit;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import pl.jakubtworek.booking.entity.Customer;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.entity.ReservationStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test nie wybiera kilku wygodnych przykładów. jqwik generuje
 * historie komend, a po znalezieniu błędu zmniejsza je (shrinking) do możliwie
 * krótkiego kontrprzykładu. Dzięki temu raport pokazuje np. dwie konkretne
 * operacje łamiące niezmiennik, zamiast losowej historii z czterdziestu kroków.
 */
class ReservationLifecyclePropertyTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-01T10:00:00Z");

    @Property(tries = 500, seed = "20260913")
    void everyGeneratedHistoryReleasesCapacityAtMostOnce(
            @ForAll("histories") List<TimedOperation> history
    ) {
        Reservation reservation = new Reservation(
                new Event("Property event", "Warsaw", "test", OffsetDateTime.parse("2026-07-01T10:00:00Z")),
                new Customer("Property Customer", "property@example.test", CREATED_AT),
                CREATED_AT,
                Duration.ofMinutes(15)
        );
        int releases = 0;

        for (TimedOperation command : history) {
            try {
                boolean released = switch (command.type()) {
                    case CONFIRM -> {
                        reservation.confirm(command.at());
                        yield false;
                    }
                    case PAYMENT_TIMEOUT -> {
                        reservation.markPaymentTimeout(command.at());
                        yield false;
                    }
                    case CANCEL -> reservation.cancel(command.at());
                    case EXPIRE -> reservation.expire(command.at());
                };
                if (released) {
                    releases++;
                }
            } catch (IllegalStateException rejectedTransition) {
                // Odrzucona komenda jest legalnym wynikiem generowanej historii.
                // Property dotyczy stanu po wszystkich zaakceptowanych przejściach.
            }
        }

        assertThat(releases)
                .as("jedna rezerwacja nie może zwolnić dwóch miejsc")
                .isLessThanOrEqualTo(1);
        assertTimestampInvariant(reservation);
    }

    @Provide
    Arbitrary<List<TimedOperation>> histories() {
        Arbitrary<OperationType> type = Arbitraries.of(OperationType.values());
        Arbitrary<Long> secondsAfterCreation = Arbitraries.longs().between(0, 1_200);
        return Combinators.combine(type, secondsAfterCreation)
                .as((operation, offset) -> new TimedOperation(operation, CREATED_AT.plusSeconds(offset)))
                .list()
                .ofMaxSize(40);
    }

    private static void assertTimestampInvariant(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            assertThat(reservation.getConfirmedAt()).isNotNull();
            assertThat(reservation.getCancelledAt()).isNull();
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            assertThat(reservation.getCancelledAt()).isNotNull();
            assertThat(reservation.getConfirmedAt()).isNull();
        }
    }

    private enum OperationType {
        CONFIRM,
        PAYMENT_TIMEOUT,
        CANCEL,
        EXPIRE
    }

    private record TimedOperation(OperationType type, Instant at) {
    }
}
