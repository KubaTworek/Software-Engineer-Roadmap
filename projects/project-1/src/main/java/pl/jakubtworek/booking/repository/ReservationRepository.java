package pl.jakubtworek.booking.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.jakubtworek.booking.entity.Reservation;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @EntityGraph(attributePaths = {"event", "customer"})
    Optional<Reservation> findDetailedById(UUID id);
}
