package pl.jakubtworek.booking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import pl.jakubtworek.booking.cache.AvailabilitySnapshotCache;
import pl.jakubtworek.booking.cache.EventDetailsCache;
import pl.jakubtworek.booking.config.ReservationPolicyProperties;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.time.Clock;
import java.time.Instant;

/**
 * Wykonywalny przykład trwałego schedulera: stan pracy znajduje się w bazie,
 * a nie w pamięci procesu. Restart aplikacji opóźni wykonanie, ale go nie zgubi.
 */
@Service
public class ReservationExpirationService {
    private final ReservationRepository reservationRepository;
    private final CapacityPoolRepository capacityPoolRepository;
    private final EventDetailsCache eventDetailsCache;
    private final AvailabilitySnapshotCache availabilitySnapshotCache;
    private final ReservationPolicyProperties policy;
    private final Clock clock;

    public ReservationExpirationService(
            ReservationRepository reservationRepository,
            CapacityPoolRepository capacityPoolRepository,
            EventDetailsCache eventDetailsCache,
            AvailabilitySnapshotCache availabilitySnapshotCache,
            ReservationPolicyProperties policy,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.capacityPoolRepository = capacityPoolRepository;
        this.eventDetailsCache = eventDetailsCache;
        this.availabilitySnapshotCache = availabilitySnapshotCache;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public int expireNextBatch() {
        return expireNextBatchAt(clock.instant());
    }

    int expireNextBatchAt(Instant now) {
        int expired = 0;
        for (var reservationId : reservationRepository.findExpirableIdsForUpdateSkipLocked(
                now,
                policy.expirationBatchSize()
        )) {
            var reservation = reservationRepository.findDetailedById(reservationId).orElseThrow();
            if (!reservation.expire(now)) {
                continue;
            }
            var eventId = reservation.getEvent().getId();
            if (capacityPoolRepository.releaseOneSeat(eventId) != 1) {
                throw new IllegalStateException("Could not release capacity for expired reservation " + reservationId);
            }
            evictAfterCommit(eventId);
            expired++;
        }
        return expired;
    }

    private void evictAfterCommit(java.util.UUID eventId) {
        CacheInvalidation.afterCommit(() -> eventDetailsCache.evict(eventId),
                () -> availabilitySnapshotCache.evict(eventId));
    }
}
