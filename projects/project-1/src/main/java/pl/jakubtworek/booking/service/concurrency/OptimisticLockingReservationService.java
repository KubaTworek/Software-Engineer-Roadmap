package pl.jakubtworek.booking.service.concurrency;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;

import java.util.UUID;

@Service
public class OptimisticLockingReservationService {
    private static final int MAX_RETRIES = 200;

    private final CapacityPoolRepository capacityPoolRepository;
    private final ReservationCreationSupport reservationCreationSupport;
    private final TransactionTemplate transactionTemplate;

    public OptimisticLockingReservationService(
            CapacityPoolRepository capacityPoolRepository,
            ReservationCreationSupport reservationCreationSupport,
            PlatformTransactionManager transactionManager
    ) {
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationCreationSupport = reservationCreationSupport;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public UUID create(UUID eventId, ReservationCreateRequest request) {
        OptimisticLockingFailureException lastConflict = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> createInTransaction(eventId, request, status));
            } catch (OptimisticLockingFailureException e) {
                lastConflict = e;
                backoff(attempt);
            }
        }

        throw new OptimisticLockingFailureException(
                "Could not reserve capacity after optimistic locking retries", lastConflict
        );
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(10L, attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during optimistic locking retry", e);
        }
    }

    private UUID createInTransaction(UUID eventId, ReservationCreateRequest request, TransactionStatus status) {
        try {
            Event event = reservationCreationSupport.getEvent(eventId);
            CapacityPool pool = capacityPoolRepository.findByEventId(eventId)
                    .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));

            if (pool.getAvailableCapacity() <= 0) {
                throw new CapacityUnavailableException("No available capacity for event: " + eventId);
            }

            pool.reserveOne();
            UUID reservationId = reservationCreationSupport.saveReservation(event, request).getId();
            capacityPoolRepository.flush();
            return reservationId;
        } catch (OptimisticLockingFailureException e) {
            status.setRollbackOnly();
            throw e;
        }
    }
}
