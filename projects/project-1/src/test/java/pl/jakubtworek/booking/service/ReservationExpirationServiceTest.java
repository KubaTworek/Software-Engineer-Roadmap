package pl.jakubtworek.booking.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.jakubtworek.booking.cache.AvailabilitySnapshotCache;
import pl.jakubtworek.booking.cache.EventDetailsCache;
import pl.jakubtworek.booking.config.ReservationPolicyProperties;
import pl.jakubtworek.booking.entity.Customer;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.entity.ReservationStatus;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test dowodzi całej gwarancji wygaśnięcia: zmiany stanu, dokładnie jednego
 * zwolnienia capacity i unieważnienia obu cache'y. Sam test encji nie mógłby
 * wykazać, że stan pochodny przestał być nieaktualny.
 */
class ReservationExpirationServiceTest {

    @Test
    void expiresPersistentHoldReleasesCapacityAndInvalidatesDerivedState() {
        Instant now = Instant.parse("2026-06-01T10:15:00Z");
        Event event = new Event("Expiry lab", "Warsaw", "education", OffsetDateTime.now().plusDays(1));
        Reservation reservation = new Reservation(
                event,
                new Customer("Learner", "learner@example.test"),
                now.minus(Duration.ofMinutes(15)),
                Duration.ofMinutes(15)
        );

        ReservationRepository reservations = mock(ReservationRepository.class);
        CapacityPoolRepository capacity = mock(CapacityPoolRepository.class);
        EventDetailsCache eventCache = mock(EventDetailsCache.class);
        AvailabilitySnapshotCache availabilityCache = mock(AvailabilitySnapshotCache.class);
        when(reservations.findExpirableIdsForUpdateSkipLocked(now, 25))
                .thenReturn(List.of(reservation.getId()));
        when(reservations.findDetailedById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(capacity.releaseOneSeat(event.getId())).thenReturn(1);

        ReservationExpirationService service = new ReservationExpirationService(
                reservations,
                capacity,
                eventCache,
                availabilityCache,
                new ReservationPolicyProperties(Duration.ofMinutes(15), 25),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThat(service.expireNextBatch()).isEqualTo(1);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        verify(capacity).releaseOneSeat(event.getId());
        verify(eventCache).evict(event.getId());
        verify(availabilityCache).evict(event.getId());
    }

    @Test
    void cacheInvalidationWaitsForSuccessfulCommit() {
        Instant now = Instant.parse("2026-06-01T10:15:00Z");
        Event event = new Event("Commit boundary", "Warsaw", "education", OffsetDateTime.now().plusDays(1));
        Reservation reservation = new Reservation(
                event,
                new Customer("Learner", "commit@example.test"),
                now.minus(Duration.ofMinutes(15)),
                Duration.ofMinutes(15)
        );
        ReservationRepository reservations = mock(ReservationRepository.class);
        CapacityPoolRepository capacity = mock(CapacityPoolRepository.class);
        EventDetailsCache eventCache = mock(EventDetailsCache.class);
        AvailabilitySnapshotCache availabilityCache = mock(AvailabilitySnapshotCache.class);
        when(reservations.findExpirableIdsForUpdateSkipLocked(now, 25)).thenReturn(List.of(reservation.getId()));
        when(reservations.findDetailedById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(capacity.releaseOneSeat(event.getId())).thenReturn(1);
        var service = new ReservationExpirationService(
                reservations, capacity, eventCache, availabilityCache,
                new ReservationPolicyProperties(Duration.ofMinutes(15), 25),
                Clock.fixed(now, ZoneOffset.UTC));

        /*
         * Symulujemy granicę transakcji Springa. Publikacja invalidacji przed
         * commitem tworzyłaby okno, w którym inny request odbuduje cache ze
         * starej bazy. Callback afterCommit zamyka to okno od właściwej strony.
         */
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.expireNextBatch();
            verify(eventCache, never()).evict(event.getId());
            verify(availabilityCache, never()).evict(event.getId());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());

            verify(eventCache).evict(event.getId());
            verify(availabilityCache).evict(event.getId());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
