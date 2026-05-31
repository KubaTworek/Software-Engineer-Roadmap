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

/**
 * Repozytorium JPA dla encji Reservation.
 *
 * Ta klasa nie jest tylko prostym CRUD-em. Zawiera zapytania dodane w kilku etapach:
 *
 * - MVP: pobieranie szczegółów rezerwacji,
 * - Spring pitfalls: fetch join, projection, EntityGraph,
 * - SQL/performance: DTO projections, offset pagination, keyset pagination,
 * - N+1: celowo zła metoda oraz poprawione warianty.
 *
 * JpaRepository<Reservation, UUID> zapewnia podstawowe metody:
 *
 * - save(...),
 * - findById(...),
 * - delete(...),
 * - existsById(...),
 * - findAll(...).
 */
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /**
     * Pobiera rezerwację razem z relacjami potrzebnymi do zbudowania odpowiedzi API.
     *
     * @EntityGraph(attributePaths = {"event", "customer"}) mówi Hibernate:
     * "dla tej metody dociągnij także event i customer".
     *
     * Dzięki temu późniejsze wywołania:
     *
     * - reservation.getEvent().getName(),
     * - reservation.getCustomer().getEmail()
     *
     * nie powinny powodować dodatkowych lazy selectów ani LazyInitializationException.
     *
     * To jest wygodne rozwiązanie, gdy chcesz zachować prostą nazwę metody Spring Data,
     * ale jednocześnie kontrolować graf pobierania.
     */
    @EntityGraph(attributePaths = {"event", "customer"})
    Optional<Reservation> findDetailedById(UUID id);

    /**
     * Pobiera rezerwację razem z eventem i customerem przez JPQL fetch join.
     *
     * Fetch join robi to jawnie w zapytaniu:
     *
     * select reservation
     * from Reservation reservation
     * join fetch reservation.event
     * join fetch reservation.customer
     * where reservation.id = :id
     *
     * Efekt jest podobny do EntityGraph, ale bardziej bezpośredni:
     * patrząc na zapytanie od razu widać, które relacje zostaną pobrane.
     *
     * Ta metoda jest dobra edukacyjnie do porównania:
     *
     * - lazy loading,
     * - EntityGraph,
     * - fetch join.
     */
    @Query("""
            select reservation
              from Reservation reservation
              join fetch reservation.event
              join fetch reservation.customer
             where reservation.id = :id
            """)
    Optional<Reservation> findByIdUsingFetchJoin(@Param("id") UUID id);

    /**
     * Pobiera rezerwację jako DTO projection, bez materializowania pełnej encji.
     *
     * Zapytanie tworzy bezpośrednio SpringPitfallReservationView:
     *
     * new pl.jakubtworek.booking.dto.SpringPitfallReservationView(...)
     *
     * Zalety projection:
     *
     * - pobierasz tylko pola potrzebne do konkretnego widoku,
     * - unikasz lazy loadingu,
     * - nie wystawiasz encji JPA poza warstwę persystencji,
     * - często generujesz prostszy SQL.
     *
     * Wady:
     *
     * - projection jest mniej elastyczna niż encja,
     * - jeśli potrzebujesz wielu wariantów odpowiedzi, możesz mieć wiele DTO/query.
     */
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

    /**
     * Pobiera rezerwacje organizacji po statusie jako DTO projection.
     *
     * To zapytanie jest zaprojektowane pod access pattern:
     *
     * organizationId + status + sortowanie po createdAt desc
     *
     * Dlatego w etapie SQL/performance sensowny jest indeks:
     *
     * CREATE INDEX idx_reservation_org_status_created_at
     * ON reservations(organization_id, status, created_at DESC);
     *
     * Zwracamy Page<ReservationListItemResponse>, więc Spring Data wykona także
     * zapytanie count, żeby policzyć całkowitą liczbę wyników.
     *
     * To jest wygodne dla UI, ale przy bardzo dużych tabelach count może być kosztowny.
     */
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

    /**
     * Pobiera rezerwacje klienta przez offset pagination.
     *
     * Offset pagination działa przez page/size, np.:
     *
     * - page=0, size=50,
     * - page=1, size=50,
     * - page=1000, size=50.
     *
     * Problem: im większy offset, tym więcej rekordów baza musi pominąć.
     *
     * To jest proste API, ale przy dużych datasetach keyset pagination często
     * będzie stabilniejsze wydajnościowo.
     */
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

    /**
     * Pobiera rezerwacje klienta przez uproszczoną keyset pagination.
     *
     * Zamiast pytać o konkretny numer strony, klient przekazuje kursor:
     *
     * afterCreatedAt
     *
     * Zapytanie zwraca rekordy starsze niż ostatni rekord z poprzedniej strony:
     *
     * reservation.createdAt < :afterCreatedAt
     *
     * To pozwala bazie iść po indeksie zamiast pomijać tysiące rekordów offsetem.
     *
     * Uwaga: to jest uproszczona wersja. Ponieważ sortowanie jest:
     *
     * order by createdAt desc, id desc
     *
     * pełny cursor powinien zawierać:
     *
     * - afterCreatedAt,
     * - afterId.
     *
     * Bez afterId można pominąć albo zdublować rekordy, jeśli wiele rezerwacji
     * ma identyczny createdAt.
     */
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

    /**
     * Liczy rezerwacje eventu pogrupowane po statusie.
     *
     * To jest przykład agregacji po stronie bazy:
     *
     * select status, count(id)
     * from reservations
     * where event_id = ?
     * group by status
     *
     * Takie liczenie w SQL jest zwykle lepsze niż pobranie wszystkich rezerwacji
     * do pamięci aplikacji i liczenie ich w Javie.
     *
     * Metoda zwraca List<Object[]>, bo jest to prosty wariant edukacyjny.
     * Produkcyjnie można rozważyć projection typu:
     *
     * record ReservationStatusCount(ReservationStatus status, long count) {}
     */
    @Query("""
            select reservation.status, count(reservation.id)
              from Reservation reservation
             where reservation.event.id = :eventId
             group by reservation.status
            """)
    List<Object[]> countByStatusForEvent(@Param("eventId") UUID eventId);

    /**
     * Celowo naiwny wariant do pokazania problemu N+1.
     *
     * Ta metoda pobiera tylko Reservation.
     * Jeśli później kod mapujący DTO wywoła:
     *
     * - reservation.getEvent().getName(),
     * - reservation.getCustomer().getEmail(),
     *
     * Hibernate może wykonać dodatkowe zapytania dla relacji.
     *
     * To właśnie jest N+1:
     *
     * - 1 zapytanie po listę rezerwacji,
     * - N dodatkowych zapytań po relacje.
     *
     * Ta metoda powinna istnieć jako przypadek edukacyjny, a nie jako docelowy kod.
     */
    @Query("""
            select reservation
              from Reservation reservation
             where reservation.event.id = :eventId
             order by reservation.createdAt desc
            """)
    List<Reservation> findByEventIdNaive(@Param("eventId") UUID eventId);

    /**
     * Poprawia problem N+1 przez fetch join.
     *
     * Jednym zapytaniem pobieramy:
     *
     * - Reservation,
     * - powiązany Event,
     * - powiązanego Customer.
     *
     * Dzięki temu mapowanie DTO nie musi dociągać relacji osobnymi SELECT-ami.
     */
    @Query("""
            select reservation
              from Reservation reservation
              join fetch reservation.event
              join fetch reservation.customer
             where reservation.event.id = :eventId
             order by reservation.createdAt desc
            """)
    List<Reservation> findByEventIdUsingFetchJoin(@Param("eventId") UUID eventId);

    /**
     * Poprawia problem N+1 przez EntityGraph.
     *
     * EntityGraph mówi Hibernate, że dla tej metody ma pobrać także:
     *
     * - event,
     * - customer.
     *
     * W porównaniu do fetch join:
     *
     * - zapytanie JPQL pozostaje prostsze,
     * - graf pobierania jest opisany adnotacją,
     * - łatwo zmieniać strategię pobierania bez przepisywania całego query.
     */
    @EntityGraph(attributePaths = {"event", "customer"})
    @Query("""
            select reservation
              from Reservation reservation
             where reservation.event.id = :eventId
             order by reservation.createdAt desc
            """)
    List<Reservation> findByEventIdUsingEntityGraph(@Param("eventId") UUID eventId);
}