package pl.jakubtworek.backend.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import pl.jakubtworek.backend.reservation.domain.ReservationEntity;
import pl.jakubtworek.backend.reservation.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {
    List<ReservationEntity> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant now);

    /** Blokada bazy, a nie JVM, serializuje confirm/cancel/expire między replikami. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationEntity r where r.id = :id")
    Optional<ReservationEntity> findByIdForUpdate(UUID id);

    @Query(value = "SELECT * FROM reservations WHERE status='PENDING' AND expires_at < :now " +
            "ORDER BY expires_at FOR UPDATE SKIP LOCKED LIMIT 100", nativeQuery = true)
    List<ReservationEntity> lockExpiredBatch(Instant now);
}
