package pl.jakubtworek.booking.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.*;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.entity.ReservationStatus;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serwis edukacyjny dla Etapu 5 — SQL i performance.
 *
 * Jego celem nie jest ukrywanie całej logiki eventów/rezerwacji, tylko pokazanie:
 * - zapytań pod konkretne access patterny,
 * - offset pagination,
 * - keyset pagination,
 * - problemu N+1,
 * - napraw przez fetch join i EntityGraph,
 * - DTO projections,
 * - ręcznego uruchamiania EXPLAIN ANALYZE.
 *
 * W normalnym projekcie nazwa SqlPerformanceService byłaby raczej zbyt techniczna.
 * Produkcyjnie można by rozważyć nazwy typu EventQueryService albo ReservationQueryService.
 * Tutaj techniczna nazwa jest celowa, bo ten serwis pełni rolę materiału edukacyjnego.
 */
@Service
public class SqlPerformanceService {

    /**
     * Maksymalny rozmiar strony.
     *
     * To prosta ochrona API przed requestami typu size=100000.
     * Nawet jeśli baza ma indeksy, zbyt duża strona może obciążyć:
     * - bazę danych,
     * - pamięć aplikacji,
     * - serializację JSON,
     * - sieć.
     */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * Repozytorium eventów.
     *
     * Używane tutaj głównie do zapytań wyszukujących eventy po city/category/from
     * oraz do sprawdzenia, czy event istnieje przed liczeniem statystyk.
     */
    private final EventRepository eventRepository;

    /**
     * Repozytorium rezerwacji.
     *
     * Zawiera zapytania zoptymalizowane pod konkretne przypadki:
     * - rezerwacje organizacji po statusie,
     * - rezerwacje klienta offset pagination,
     * - rezerwacje klienta keyset pagination,
     * - N+1 / fetch join / EntityGraph,
     * - agregacje po statusie.
     */
    private final ReservationRepository reservationRepository;

    /**
     * JdbcTemplate jest używany do ręcznego wykonania EXPLAIN ANALYZE.
     *
     * JPA dobrze nadaje się do większości operacji aplikacyjnych, ale przy analizie
     * planu wykonania wygodniej jest odpalić dokładny SQL i dostać tekstowy plan
     * bezpośrednio z bazy.
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Constructor injection.
     *
     * Dzięki temu zależności są jawne, pola są final, a serwis jest łatwiejszy
     * do testowania.
     */
    public SqlPerformanceService(EventRepository eventRepository,
                                 ReservationRepository reservationRepository,
                                 JdbcTemplate jdbcTemplate) {
        this.eventRepository = eventRepository;
        this.reservationRepository = reservationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Wyszukuje eventy po access patternie:
     *
     * city + category + startsAt >= from
     *
     * To zapytanie jest celowo zaprojektowane pod indeks złożony:
     *
     * CREATE INDEX idx_event_city_category_start_time
     * ON events(city, category, starts_at);
     *
     * Taki indeks ma sens, ponieważ:
     * - city i category są filtrami równościowymi,
     * - starts_at jest filtrem zakresowym i jednocześnie może wspierać sortowanie.
     *
     * Metoda zwraca DTO, a nie encje JPA, żeby ograniczyć przypadkowy lazy loading
     * i nie wiązać API ze strukturą encji.
     */
    @Transactional(readOnly = true)
    public List<EventSearchResponse> searchEvents(String city, OffsetDateTime from, String category) {
        return eventRepository.searchByCityCategoryAndFrom(city, category, from)
                .stream()
                .map(this::toEventSearchResponse)
                .toList();
    }

    /**
     * Pobiera rezerwacje organizacji po statusie z offset pagination.
     *
     * Access pattern:
     *
     * organizationId + status + page/size
     *
     * To pasuje do indeksu:
     *
     * CREATE INDEX idx_reservation_org_status_created_at
     * ON reservations(organization_id, status, created_at DESC);
     *
     * Offset pagination jest prosta dla API, ale przy dużych offsetach może być droga:
     * baza musi przejść przez wiele rekordów, zanim zwróci właściwą stronę.
     */
    @Transactional(readOnly = true)
    public Page<ReservationListItemResponse> organizationReservations(UUID organizationId,
                                                                      ReservationStatus status,
                                                                      int page,
                                                                      int size) {
        return reservationRepository.findOrganizationReservationsByStatus(
                organizationId,
                status,
                PageRequest.of(Math.max(page, 0), limit(size))
        );
    }

    /**
     * Pobiera rezerwacje klienta przez offset pagination.
     *
     * Ten wariant jest wygodny, bo klient może powiedzieć:
     *
     * page=0, page=1, page=2...
     *
     * Problem pojawia się przy dużych zbiorach danych i dużych offsetach.
     * Przykładowo page=10000 przy size=50 oznacza, że baza musi pominąć bardzo
     * dużo rekordów.
     */
    @Transactional(readOnly = true)
    public Page<ReservationListItemResponse> customerReservationsOffset(UUID customerId, int page, int size) {
        return reservationRepository.findCustomerReservationsOffset(
                customerId,
                PageRequest.of(Math.max(page, 0), limit(size))
        );
    }

    /**
     * Pobiera rezerwacje klienta przez keyset pagination.
     *
     * Zamiast pytać o "stronę numer 1000", klient pyta:
     *
     * "daj mi kolejne rekordy po createdAt ostatniego rekordu z poprzedniej strony".
     *
     * Zalety:
     * - stabilniejsza wydajność przy dużych danych,
     * - lepsze wykorzystanie indeksu,
     * - brak kosztownego pomijania tysięcy rekordów.
     *
     * Wady:
     * - trudniej przejść bezpośrednio do dowolnej strony,
     * - API musi zwracać cursor,
     * - sortowanie musi być stabilne.
     *
     * W tej implementacji cursor składa się z:
     * - createdAt,
     * - id.
     *
     * Samo createdAt może nie wystarczyć, bo kilka rezerwacji może mieć ten sam czas.
     */
    @Transactional(readOnly = true)
    public KeysetReservationPageResponse customerReservationsKeyset(UUID customerId,
                                                                    Instant afterCreatedAt,
                                                                    int size) {
        int requested = limit(size);

        /*
         * Pobieramy o jeden rekord więcej niż użytkownik poprosił.
         *
         * Dzięki temu możemy sprawdzić, czy istnieje kolejna strona,
         * bez wykonywania osobnego COUNT(*).
         */
        List<ReservationListItemResponse> fetched = reservationRepository.findCustomerReservationsKeyset(
                customerId,
                afterCreatedAt,
                PageRequest.of(0, requested + 1)
        );

        boolean hasNext = fetched.size() > requested;

        /*
         * Jeśli pobraliśmy dodatkowy rekord techniczny, nie zwracamy go klientowi.
         * Służył tylko do ustalenia hasNext.
         */
        List<ReservationListItemResponse> items = hasNext ? fetched.subList(0, requested) : fetched;

        /*
         * Ostatni zwrócony element staje się podstawą kursora dla następnej strony.
         */
        ReservationListItemResponse last = items.isEmpty() ? null : items.get(items.size() - 1);

        return new KeysetReservationPageResponse(
                items,
                last == null ? null : last.createdAt(),
                last == null ? null : last.id(),
                hasNext
        );
    }

    /**
     * Liczy statystyki rezerwacji dla eventu.
     *
     * Zamiast pobierać wszystkie rezerwacje do Javy i liczyć je w pamięci,
     * repozytorium wykonuje agregację po stronie bazy:
     *
     * SELECT status, COUNT(*)
     * FROM reservations
     * WHERE event_id = ?
     * GROUP BY status
     *
     * To jest ważny pattern: baza danych jest zwykle lepszym miejscem do agregacji
     * niż aplikacja, jeśli agregujemy duży zbiór rekordów.
     */
    @Transactional(readOnly = true)
    public EventStatsResponse eventStats(UUID eventId) {
        /*
         * Najpierw sprawdzamy, czy event istnieje.
         *
         * Bez tego event bez rezerwacji i nieistniejący event wyglądałyby podobnie:
         * oba mogłyby dać puste counts.
         */
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Event not found: " + eventId);
        }

        /*
         * EnumMap jest wydajniejszy i semantycznie lepiej pasuje do kluczy enum
         * niż zwykły HashMap.
         */
        Map<ReservationStatus, Long> counts = new EnumMap<>(ReservationStatus.class);

        /*
         * Repozytorium zwraca surowe wiersze: [status, count].
         *
         * To prosty wariant edukacyjny. Alternatywnie można zrobić projection DTO
         * bezpośrednio w zapytaniu.
         */
        for (Object[] row : reservationRepository.countByStatusForEvent(eventId)) {
            counts.put((ReservationStatus) row[0], (Long) row[1]);
        }

        long pending = counts.getOrDefault(ReservationStatus.PENDING, 0L);
        long confirmed = counts.getOrDefault(ReservationStatus.CONFIRMED, 0L);
        long cancelled = counts.getOrDefault(ReservationStatus.CANCELLED, 0L);
        long paymentTimeout = counts.getOrDefault(ReservationStatus.PAYMENT_TIMEOUT, 0L);

        return new EventStatsResponse(
                eventId,
                pending + confirmed + cancelled + paymentTimeout,
                pending,
                confirmed,
                cancelled,
                paymentTimeout
        );
    }

    /**
     * Celowo naiwny wariant pokazujący problem N+1.
     *
     * findByEventIdNaive(...) pobiera rezerwacje.
     * Następnie mapowanie do DTO odwołuje się do:
     *
     * reservation.getEvent().getName()
     * reservation.getCustomer().getEmail()
     *
     * Jeśli relacje są lazy, Hibernate może wykonać dodatkowe zapytania dla każdej
     * rezerwacji lub grupy rezerwacji. To właśnie problem N+1.
     *
     * Ta metoda istnieje po to, żeby zobaczyć problem w logach SQL/profilerze,
     * a nie po to, żeby używać jej produkcyjnie.
     */
    @Transactional(readOnly = true)
    public List<ReservationListItemResponse> eventReservationsNaiveNPlusOne(UUID eventId) {
        List<Reservation> reservations = reservationRepository.findByEventIdNaive(eventId);
        return reservations.stream().map(this::toReservationListItemResponse).toList();
    }

    /**
     * Naprawa N+1 przez fetch join.
     *
     * Repozytorium powinno wykonać zapytanie w stylu:
     *
     * SELECT r
     * FROM Reservation r
     * JOIN FETCH r.event
     * JOIN FETCH r.customer
     * WHERE r.event.id = :eventId
     *
     * Dzięki temu potrzebne relacje są pobrane jednym zapytaniem.
     */
    @Transactional(readOnly = true)
    public List<ReservationListItemResponse> eventReservationsFetchJoin(UUID eventId) {
        List<Reservation> reservations = reservationRepository.findByEventIdUsingFetchJoin(eventId);
        return reservations.stream().map(this::toReservationListItemResponse).toList();
    }

    /**
     * Naprawa N+1 przez EntityGraph.
     *
     * EntityGraph pozwala zadeklarować, które relacje mają zostać dociągnięte
     * dla konkretnej metody repozytorium, bez wpisywania fetch join w JPQL.
     *
     * To bywa czytelniejsze, gdy chcemy sterować grafem pobierania z poziomu
     * repozytorium.
     */
    @Transactional(readOnly = true)
    public List<ReservationListItemResponse> eventReservationsEntityGraph(UUID eventId) {
        List<Reservation> reservations = reservationRepository.findByEventIdUsingEntityGraph(eventId);
        return reservations.stream().map(this::toReservationListItemResponse).toList();
    }

    /**
     * Uruchamia EXPLAIN ANALYZE dla zapytania wyszukiwania eventów.
     *
     * EXPLAIN ANALYZE nie tylko pokazuje przewidywany plan, ale naprawdę wykonuje
     * zapytanie i pokazuje czasy rzeczywiste.
     *
     * To jest ważne:
     * - EXPLAIN pokazuje estymacje,
     * - EXPLAIN ANALYZE pokazuje wykonanie.
     *
     * Uwaga praktyczna:
     * EXPLAIN ANALYZE dla SELECT jest bezpieczne logicznie, ale nadal wykonuje pracę.
     * Dla UPDATE/DELETE/INSERT trzeba uważać, bo EXPLAIN ANALYZE wykona modyfikację,
     * chyba że opakujesz to w transakcję i zrobisz rollback.
     */
    public ExplainAnalyzeResponse explainEventSearch(String city, String category, OffsetDateTime from) {
        List<String> plan = jdbcTemplate.queryForList("""
                EXPLAIN ANALYZE
                SELECT id, name, city, category, starts_at, status
                  FROM events
                 WHERE city = ?
                   AND category = ?
                   AND starts_at >= ?
                 ORDER BY starts_at ASC
                """, String.class, city, category, from);

        return new ExplainAnalyzeResponse("event-search", plan);
    }

    /**
     * Mapuje encję Event na lekkie DTO listy wyszukiwania.
     *
     * Nie zwracamy pełnego EventResponse, bo search endpoint zwykle potrzebuje
     * mniejszego zestawu danych niż szczegóły eventu.
     */
    private EventSearchResponse toEventSearchResponse(Event event) {
        return new EventSearchResponse(
                event.getId(),
                event.getName(),
                event.getCity(),
                event.getCategory(),
                event.getStartsAt(),
                event.getStatus()
        );
    }

    /**
     * Mapuje Reservation na DTO listowe.
     *
     * Ta metoda celowo odwołuje się do relacji:
     *
     * - reservation.getEvent()
     * - reservation.getCustomer()
     *
     * Dzięki temu dobrze pokazuje różnicę między:
     * - naiwnym N+1,
     * - fetch join,
     * - EntityGraph.
     */
    private ReservationListItemResponse toReservationListItemResponse(Reservation reservation) {
        return new ReservationListItemResponse(
                reservation.getId(),
                reservation.getEvent().getId(),
                reservation.getEvent().getName(),
                reservation.getCustomer().getId(),
                reservation.getCustomer().getEmail(),
                reservation.getStatus(),
                reservation.getCreatedAt()
        );
    }

    /**
     * Normalizuje parametr size z requestu.
     *
     * Jeśli użytkownik poda size <= 0, zwracamy domyślne 20.
     * Jeśli poda size większe niż MAX_PAGE_SIZE, obcinamy do MAX_PAGE_SIZE.
     *
     * To prosta, ale ważna ochrona endpointów listujących dane.
     */
    private int limit(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}