package pl.jakubtworek.booking.service.concurrency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;

import java.util.UUID;

@Service
public class AtomicSqlReservationService {
    private final CapacityPoolRepository capacityPoolRepository;
    private final ReservationCreationSupport reservationCreationSupport;

    public AtomicSqlReservationService(
            CapacityPoolRepository capacityPoolRepository,
            ReservationCreationSupport reservationCreationSupport
    ) {
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationCreationSupport = reservationCreationSupport;
    }

    /**
     * Preferowana strategia w tym projekcie: warunek i modyfikacja są jednym atomowym UPDATE-em.
     */
    @Transactional
    public UUID create(UUID eventId, ReservationCreateRequest request) {
        Event event = reservationCreationSupport.getEvent(eventId);
        int updatedRows = capacityPoolRepository.reserveOneSeatIfAvailable(eventId);
        if (updatedRows != 1) {
            throw new CapacityUnavailableException("No available capacity for event: " + eventId);
        }
        return reservationCreationSupport.saveReservation(event, request).getId();
    }
}
