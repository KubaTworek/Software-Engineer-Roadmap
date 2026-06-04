package pl.jakubtworek.backend.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.jakubtworek.backend.reservation.domain.ReservationEntity;
import pl.jakubtworek.backend.reservation.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {
    List<ReservationEntity> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant now);
}
