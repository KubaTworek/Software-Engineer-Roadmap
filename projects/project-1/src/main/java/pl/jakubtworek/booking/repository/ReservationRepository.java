package pl.jakubtworek.booking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.jakubtworek.booking.dto.ReservationListItemResponse;
import pl.jakubtworek.booking.dto.SpringPitfallReservationView;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.entity.ReservationStatus;

import java.time.Instant;
import java.util.List;
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

    @Query("""
            select new pl.jakubtworek.booking.dto.ReservationListItemResponse(
                reservation.id,
                event.id,
                event.name,
                customer.id,
                customer.email,
                reservation.status,
                reservation.createdAt
            )
              from Reservation reservation
              join reservation.event event
              join reservation.customer customer
             where reservation.organization.id = :organizationId
               and reservation.status = :status
             order by reservation.createdAt desc, reservation.id desc
            """)
    Page<ReservationListItemResponse> findOrganizationReservationsByStatus(
            @Param("organizationId") UUID organizationId,
            @Param("status") ReservationStatus status,
            Pageable pageable
    );

    @Query("""
            select new pl.jakubtworek.booking.dto.ReservationListItemResponse(
                reservation.id,
                event.id,
                event.name,
                customer.id,
                customer.email,
                reservation.status,
                reservation.createdAt
            )
              from Reservation reservation
              join reservation.event event
              join reservation.customer customer
             where customer.id = :customerId
             order by reservation.createdAt desc, reservation.id desc
            """)
    Page<ReservationListItemResponse> findCustomerReservationsOffset(
            @Param("customerId") UUID customerId,
            Pageable pageable
    );

    @Query("""
            select new pl.jakubtworek.booking.dto.ReservationListItemResponse(
                reservation.id,
                event.id,
                event.name,
                customer.id,
                customer.email,
                reservation.status,
                reservation.createdAt
            )
              from Reservation reservation
              join reservation.event event
              join reservation.customer customer
             where customer.id = :customerId
               and (:afterCreatedAt is null or reservation.createdAt < :afterCreatedAt)
             order by reservation.createdAt desc, reservation.id desc
            """)
    List<ReservationListItemResponse> findCustomerReservationsKeyset(
            @Param("customerId") UUID customerId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            Pageable pageable
    );

    @Query("""
            select reservation.status, count(reservation.id)
              from Reservation reservation
             where reservation.event.id = :eventId
             group by reservation.status
            """)
    List<Object[]> countByStatusForEvent(@Param("eventId") UUID eventId);

    @Query("""
            select reservation
              from Reservation reservation
             where reservation.event.id = :eventId
             order by reservation.createdAt desc
            """)
    List<Reservation> findByEventIdNaive(@Param("eventId") UUID eventId);

    @Query("""
            select reservation
              from Reservation reservation
              join fetch reservation.event
              join fetch reservation.customer
             where reservation.event.id = :eventId
             order by reservation.createdAt desc
            """)
    List<Reservation> findByEventIdUsingFetchJoin(@Param("eventId") UUID eventId);

    @EntityGraph(attributePaths = {"event", "customer"})
    @Query("""
            select reservation
              from Reservation reservation
             where reservation.event.id = :eventId
             order by reservation.createdAt desc
            """)
    List<Reservation> findByEventIdUsingEntityGraph(@Param("eventId") UUID eventId);
}
