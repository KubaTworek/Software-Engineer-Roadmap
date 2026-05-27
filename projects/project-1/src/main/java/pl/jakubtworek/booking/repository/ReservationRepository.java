package pl.jakubtworek.booking.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.jakubtworek.booking.dto.SpringPitfallReservationView;
import pl.jakubtworek.booking.entity.Reservation;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @EntityGraph(attributePaths = {"event", "customer"})
    Optional<Reservation> findDetailedById(UUID id);

    @Query("""
            select reservation
              from Reservation reservation
              join fetch reservation.event
              join fetch reservation.customer
             where reservation.id = :id
            """)
    Optional<Reservation> findByIdUsingFetchJoin(@Param("id") UUID id);

    @Query("""
            select new pl.jakubtworek.booking.dto.SpringPitfallReservationView(
                reservation.id,
                event.id,
                event.name,
                customer.id,
                customer.email,
                reservation.status
            )
              from Reservation reservation
              join reservation.event event
              join reservation.customer customer
             where reservation.id = :id
            """)
    Optional<SpringPitfallReservationView> findProjectionById(@Param("id") UUID id);
}
