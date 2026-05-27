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
public class NaiveReservationService {
    private final CapacityPoolRepository capacityPoolRepository;
    private final ReservationCreationSupport reservationCreationSupport;

    public NaiveReservationService(
            CapacityPoolRepository capacityPoolRepository,
            ReservationCreationSupport reservationCreationSupport
    ) {
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationCreationSupport = reservationCreationSupport;
    }

    /**
     * Celowo błędna implementacja do Etapu 2.
     *
     * Problem: check-then-act i read-modify-write są rozdzielone.
     * Wiele transakcji może przeczytać tę samą wartość availableCapacity > 0,
     * a następnie każda z nich zapisze rezerwację. Blind update celowo omija @Version,
     * żeby test pokazał klasyczny lost update/overselling.
     */
    @Transactional
    public UUID create(UUID eventId, ReservationCreateRequest request) {
        Event event = reservationCreationSupport.getEvent(eventId);
        CapacityPool pool = capacityPoolRepository.findByEventId(eventId)
                .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));

        if (pool.getAvailableCapacity() <= 0) {
            throw new CapacityUnavailableException("No available capacity for event: " + eventId);
        }

        widenRaceWindow();

        int newAvailableCapacity = pool.getAvailableCapacity() - 1;
        capacityPoolRepository.blindSetAvailableCapacity(pool.getId(), newAvailableCapacity);

        return reservationCreationSupport.saveReservation(event, request).getId();
    }

    private void widenRaceWindow() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reproducing race condition", e);
        }
    }
}
