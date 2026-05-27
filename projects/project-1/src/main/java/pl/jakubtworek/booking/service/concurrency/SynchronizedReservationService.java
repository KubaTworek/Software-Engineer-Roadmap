package pl.jakubtworek.booking.service.concurrency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;

import java.util.UUID;

@Service
public class SynchronizedReservationService {
    private final CapacityPoolRepository capacityPoolRepository;
    private final ReservationCreationSupport reservationCreationSupport;

    public SynchronizedReservationService(
            CapacityPoolRepository capacityPoolRepository,
            ReservationCreationSupport reservationCreationSupport
    ) {
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationCreationSupport = reservationCreationSupport;
    }

    /**
     * Działa tylko jako lokalna ochrona w jednej instancji JVM.
     * Nie jest to rozwiązanie produkcyjne dla wielu instancji aplikacji.
     */
    @Transactional
    public synchronized UUID create(UUID eventId, ReservationCreateRequest request) {
        Event event = reservationCreationSupport.getEvent(eventId);
        CapacityPool pool = capacityPoolRepository.findByEventId(eventId)
                .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));

        if (pool.getAvailableCapacity() <= 0) {
            throw new CapacityUnavailableException("No available capacity for event: " + eventId);
        }

        pool.reserveOne();
        return reservationCreationSupport.saveReservation(event, request).getId();
    }
}
